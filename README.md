# ibmobile-api-tests

Контрактные API-тесты бэкенда розничного интернет-банка (ibMobile, REST/JSON).
Стек: Java 25, Maven, JUnit 5, Rest Assured, Allure, AssertJ, Jackson.

Что проверяем:
- **контракт** — каждый ответ сверяется со строгой JSON-схемой (лишнее поле — ошибка);
- **бизнес-логику** — ключевые значения ответа и бизнес-отказы проверок с точным текстом;
- **ошибки сервера** — HTTP не 200 или недопустимый errorCode роняет любой тест.

Чего не проверяем: остатки, сквозные e2e-сценарии, мусорный ввод на каждое поле.

## Быстрый старт

1. JDK 25. Maven ставить не нужно — в репозитории есть `mvnw`.
2. Файл `~/.ibmobile/secrets.properties` (вне репозитория, в git не попадает):

```properties
# <env>.<профиль>.login / .password, <env>.db.user / .password
dev.MAIN.login=...
dev.MAIN.password=...
#test.MAIN.login=
#test.db.user=
```

3. Запуск:

```
mvnw test                        # все тесты, каждый на своём контуре
mvnw test -Dtest=AccListTest     # один класс
mvnw test -DexcludedGroups=prod  # без тестов prod (-Dgroups=test — только test)
mvnw allure:serve                # отчёт
```

Из IDEA тесты запускаются как обычные JUnit-тесты.

## Контуры

Контур задаётся у теста, а не у прогона: по умолчанию dev, `@OnTest` / `@OnProd` на методе или классе — test / prod.
Тест идёт туда, где стоит версия с нужной задачей.

| Контур | Аннотация | Логины | Параллельно |
|---|---|---|---|
| dev | — | тестовые, мультисессия | 2 потока (`junit-platform.properties`) |
| test | `@OnTest` | только свой логин, одна сессия | по одному (`@ResourceLock("test")`) |
| prod | `@OnProd` | только свой логин, одна сессия | по одному (`@ResourceLock("prod")`) |

`@OnTest` / `@OnProd` — ещё и теги `test` / `prod` для `-Dgroups` / `-DexcludedGroups`.

## Структура

```
src/test/java/ru/avangard/qa/ibmobile/
  config/   Config, Profile                   откуда берутся настройки контура, логины
  api/      Endpoint, DocType, Request,       ручки, типы документов, запрос, проверки ответа,
            ApiResponse, ErrorCode, Session    допустимые ошибки, сессия (логин/логаут)
  core/     BaseTest, Login, OnTest, OnProd,   база тестов, профиль и контур теста,
            Template, Confirm, Db              тело и подтверждение документов, чтение БД
  data/     <Ручка>Data                        ответы ручек как данные для других тестов
  tests/    Section, <домен>/<Ручка>Test       тесты; Section — группы в Allure
  tools/    SchemaDraft                        черновики JSON-схем из реальных ответов
src/test/resources/
  env/<env>.properties                         настройки контура
  versions/<релиз>.properties                  версии ручек релиза
  schemas/<ручка>/[<doc_type>/]<версия>.json   схемы ответов
  schemas/errors/                              схемы ошибок
  templates/<doc_type>.json                    шаблоны doc_content
  files/                                       файлы-вложения
  junit-platform.properties                    параллельный запуск JUnit
```

Зависимости пакетов идут в одну сторону: `config` ← `api` ← `core` ← `data` ← `tests`.

## Конфиг

| Где | Что |
|---|---|
| `env/<env>.properties` | всё о контуре: baseUrl, релиз, коды подтверждения (dev), БД (test/prod) |
| `versions/<релиз>.properties` | версии ручек релиза |
| `~/.ibmobile/secrets.properties` | логины, пароли профилей и БД; ключи `<env>.*` |

Секреты контура теста накладываются поверх `env/<env>.properties` без префикса: `dev.MAIN.login` → `MAIN.login`.
Пустое значение (`db.url=`) считается незаданным: обращение к нему роняет тест с понятным сообщением.

`uid` в каждом запросе — рудимент протокола, бэк принимает любой; это константа в `Request`.
Тайм-ауты запроса — соединение 10 с, ответ 2 мин.

### Релизы и версии

Пути ручек версионированы: `Auth/ticket/9.4`, `Doc/prepare_doc/8.1`. В `Endpoint` путь без версии,
версия берётся из `versions/<release>.properties` по имени константы, `release` — из `env/<env>.properties`
контура теста. Схема ищется по той же версии: `schemas/acc_list/4.0.json`. Поэтому `release` переключает и пути,
и схемы — у каждого контура свой релиз. Новый релиз — копия файла версий с правкой изменившихся строк, новые схемы
для них и `release=` в файле контура.

## Запрос и ответ

```java
Request.to(ACC_DETAILS).as(session()).param("account", account).post()
        .success()                     // не отказ
        .matchesSchema()               // строгая схема ручки
        .path("accBalanceDetails[0].account");
```

- Запрос — объект `Request`, отправка — только его `post()` (и `delete()` для логаута).
- Любая ручка, включая prepare_doc и send_doc, вызывается только через `Request.to(endpoint)`.
- `uid` подставляется сам, `as(session)` добавляет `ticket` (form-параметр); кейс «без ticket» — тот же запрос без `as`.
- `param(name, null)` убирает параметр — так в таблице кейсов пишется «без параметра».
- Тело — URLENC; у ручек, помеченных в `Endpoint` как multipart (`prepare_doc` и немногие другие), — MULTIPART.
- Коллекция в `param` — несколько одноимённых параметров (например, два `secure` в `send_doc`).
- Каждый запрос — шаг Allure с телом запроса и ответа.
- Дробные числа ответа — `BigDecimal` (Rest Assured по умолчанию читает их как `Float` с потерей точности).

### Ошибки

Бэк отвечает HTTP 200 и на успех, и на отказ. Различаем три вида:

| Вид | Как выглядит | Проверка |
|---|---|---|
| Бизнес-отказ (эталон) | `responseCode -1`, `resultCode FAILED`, `checkResult[{badItem, errorMessage}]` | `rejected(badItem, сообщение)` + схема `errors/check_failed.json` |
| Техническая ошибка | `errorCode` + `errorDesc` | `error(ErrorCode.X)` + схема формата ошибки |
| Ошибка сервера | HTTP не 200, `errorCode` вне `ErrorCode` (например, 2 `internal_server_error`) | роняет **любой** тест: «Ошибка сервера: …» |

Допустимые технические ошибки — только из `ErrorCode`:

| errorCode | errorDesc | Когда |
|---|---|---|
| 1 | authorization_required | нет ticket |
| 4 | invalid_parameters | некорректные или отсутствующие параметры |
| 5 | session_expired | ticket после логаута |
| 1000 | Некорректный логин или пароль пользователя. | неверный логин |

## Схемы

- Строгие, JSON Schema draft-04, `additionalProperties: false`; обязательные поля в `required`, остальные — опциональные.
- Прогон ручки без схемы падает и сохраняет ответ в `target/schema-samples`
  (`-Dsamples=true` — сохранять все ответы, чтобы набрать образцы).
- `tools.SchemaDraft` (запуск из IDEA) строит черновик в `src/test/resources/schemas`:
  поле обязательное, если оно есть во всех образцах. Существующие схемы не перезаписываются — для пересоздания удалить файл.
  Черновик проверить руками.
- Схемы ошибок в `schemas/errors/` написаны руками. Общие части — `$ref` на них (путь относительно схемы):
  `"$ref": "../../errors/check_failed.json#/properties/checkResult/items"`.

## Сессии и профили

Одна сессия — на один тест: одна ручка разным пользователям отдаёт разные данные.

- Все классы наследуют `BaseTest`. `session()` — сессия теста: логин при первом обращении, логаут после теста.
  Тест без `session()` не логинится.
- Профиль по умолчанию — `MAIN`. Другой — `@Login(Profile.X)` на методе теста:

```java
@Login(Profile.BLOCKED)
@DisplayName("Деталка счёта с блокировкой")
@Test
void blocked() {
    Request.to(ACC_DETAILS).as(session()).param("account", account(AccountType.DEBIT)).post().success().matchesSchema();
}
```

- Профиль — константа enum `config/Profile`, логин и пароль — в секретах `<env>.<ПРОФИЛЬ>.login/.password`.
  Нет профиля на контуре — тест пропускается.
- Логаут — тоже через `session()`: `session().logout()`; повторный логаут после теста не выполняется.
- Неверный пароль реальных профилей не тестируем (блокировка); негатив логина — несуществующий логин.

## Документы

prepare_doc и send_doc — обычные ручки, вызываются как все: `Request.to(PREPARE_DOC)`, `Request.to(SEND_DOC)`.
Тип документа — константа enum `api/DocType`; в тестовом классе — `private static final DocType DOC_TYPE = DocType.X`.
Значение `doc_type` — имя константы в нижнем регистре; по нему же ищутся шаблон и схемы документа.
Помогают два класса: `Template` — тело `doc_content`, `Confirm` — параметр `secure` для send_doc.

```java
var prepared = Request.to(PREPARE_DOC).as(session()).doc(DOC_TYPE, content()).post().success().matchesSchema(DOC_TYPE);
Request.to(SEND_DOC).as(session()).param("secure", Confirm.NONE.secure(session(), prepared))
        .post().success().matchesSchema(DOC_TYPE);
```

| `Confirm` | Что делает `secure(session, prepared)` |
|---|---|
| `NONE` | `secure` с токеном `noneAuthInit.code` |
| `SMSPWD` | `send_confirm_sms`, затем два `secure`: код SMS и пароль. Код на dev — из `env/dev.properties`, на test/prod — из БД |
| `SCRETCH` | `show_scretch`, затем код из `env/dev.properties` (только dev) |

- Способ задаётся в кейсе заранее; `secure` запрашивает код только для него и проверяет, что он есть в `authMethods`.
- Суммы — только `BigDecimal` (`ApiResponse.decimal`): double даёт `1.5699999999999998`.
  Суммы позитивных кейсов случайные (1.01–9.99), чтобы не ловить отказ-дубль.

### doc_content и параметризация кейсов

`Template.of(DOC_TYPE)` — шаблон тела из `templates/<doc_type>.json` (копия из Postman) как Jackson `ObjectNode`.
В тестовом классе есть валидный вариант по умолчанию, кейс меняет в нём поля. JSON на каждый кейс не нужен.

```java
private static ObjectNode content() {
    return Template.of(DOC_TYPE).put("text", TEXT);
}
```

Кейсы, которые меняют одно поле, — строки таблицы `@MethodSource`. `Template.set(content, поле, значение)` меняет поле,
`null` — убирает его; значение — строка, число, список или Map:

```java
static Stream<Arguments> rejections() {
    return Stream.of(
            arguments("пустой текст", "text", "", "text", "Заполните текст заявления"),
            arguments("без текста", "text", null, "text", "Заполните текст"));
}

@Story("Негативные")
@DisplayName("Заявление отклоняется проверками")
@ParameterizedTest(name = "({0})")
@MethodSource("rejections")
void rejection(String title, String field, Object value, String badItem, String message) {
    Request.to(PREPARE_DOC).as(session()).doc(DOC_TYPE, Template.set(content(), field, value))
            .post().rejected(badItem, message);
}
```

Кейс со сложной логикой (несколько полей, свои проверки) — отдельный метод.

Вложения — файлы из `files/` последними аргументами `doc`: они уходят частями `file` и сами попадают в `attachmentsList`
(тип — по расширению; неизвестное расширение роняет тест):

```java
Request.to(PREPARE_DOC).as(session()).doc(DOC_TYPE, content(), "image.png").post().success();
```

Схема документа — вариант схемы ручки: `matchesSchema(DOC_TYPE)` → `schemas/prepare_doc/<doc_type>/8.1.json`.

## Данные из других ручек

Тестовые данные (номера счетов и т. п.) берутся из ответов других ручек, а не хранятся в репозитории.
Класс `data/<Ручка>Data` хранит ответ ручки **целиком**. Данные у каждого пользователя свои, поэтому
объект создаётся в тесте из его сессии:

```java
var accList = new AccListData(session());
accList.accounts(AccountType.DEBIT);         // номера рублёвых дебетовых счетов
accList.first(AccountType.DEBIT);            // первый из них; нет счёта — кейс пропускается
accList.sameCurrency(account);               // другие счета в валюте account
accList.path("accounts.account.currency");   // любое поле — по пути
```

Нужен новый срез — путь прямо в тесте; повторяется в нескольких тестах — метод в классе `Data`.
`AccountType` — типы счетов по флагам acc_list (`_FX` — валютный); выборки — от большего `otb` к меньшему.

## Отчёт Allure

- `@Epic` — группа функций из `tests/Section` (Счета, Платежи, Заявления, Аэроэкспресс, Настройки…).
- `@Feature` — ручка или документ.
- `@Story("Негативные")` — негативные кейсы внутри фичи.
- Имя кейса — `@DisplayName` метода + изменяемая часть из `@ParameterizedTest(name = ...)`.
- Результаты прошлых прогонов очищаются перед каждым запуском.

## Тестовый класс

Все классы устроены одинаково — информационная ручка или документ. Каждый кейс читается одинаково:
`Request.to(...)` с данными по умолчанию, `.as(session())`, изменение кейса (`.param(...)` или `Template.set(...)`),
`.post()`, проверки. В классе — только данные по умолчанию, обёрток над запросом нет.

| Порядок | Что | Ручка (`AccDetailsTest`) | Документ (`IbOurAccTransferTest`) |
|---|---|---|---|
| 1 | позитивные кейсы | `details(type)` | `transfer(title, from, confirm)` |
| 2 | таблица `invalidParameters` → `error(INVALID_PARAMETERS)` | параметр запроса → значение | поле документа → значение |
| 3 | таблица `rejections` → `rejected(badItem, сообщение)` | — | поле документа → значение |
| 4 | особые кейсы — отдельные методы | — | `sameAccount()` |
| 5 | `withoutTicket`: тот же запрос без `.as(session())` → `error(AUTHORIZATION_REQUIRED)` | `withoutTicket()` | `withoutTicket()` |
| 6 | данные по умолчанию — приватные методы внизу | `account(type)` | `content()` |

Кейс таблицы меняет одно значение в валидном запросе, `null` — убирает его:
у ручки — `.param(name, value)`, у документа — `Template.set(content, field, value)`.

## Новая ручка

1. Константа в `api/Endpoint` (путь без версии, название для отчёта, `true` — MULTIPART) и версия в `versions/*.properties`.
2. Класс `tests/<домен>/<Ручка>Test extends BaseTest` с `@Epic` и `@Feature`. Кейсы — параметры.
3. Позитивные кейсы: `success()`, `matchesSchema()`, проверка ключевых значений.
4. Негативные кейсы — обязательно, `@Story("Негативные")`: бизнес-отказы `rejected(...)`, технические `error(...)`.
5. Документы — константа в `api/DocType`, шаблон `templates/<doc_type>.json`, в классе `DOC_TYPE`.
6. Прогнать: без схемы тест упадёт и сохранит ответ → `tools.SchemaDraft` → проверить схему → прогнать снова.
7. Не dev — `@OnTest` / `@OnProd`; на prod только то, что безопасно под своим логином.

```java
@Epic(Section.ACCOUNTS)
@Feature("Список счетов")
class AccListTest extends BaseTest {

    @DisplayName("Счета клиента с уникальными 20-значными номерами")
    @Test
    void accList() {
        List<String> codes = Request.to(ACC_LIST).as(session()).post().success().matchesSchema().path("accounts.account.code");
        assertThat(codes).isNotEmpty().doesNotHaveDuplicates();
    }

    @Story("Негативные")
    @DisplayName("Запрос без ticket отклоняется")
    @Test
    void withoutTicket() {
        Request.to(ACC_LIST).post().error(AUTHORIZATION_REQUIRED);
    }
}
```

## Параллельность

JUnit гоняет классы и кейсы параллельно в 2 потока (`junit-platform.properties`); тесты test и prod —
по одному на контур (`@ResourceLock` в `@OnTest` / `@OnProd`), параллельно с dev. Контур — на поток теста.
У каждого теста свой экземпляр класса и своя сессия, поэтому документы тоже идут параллельно:
подготовленный документ живёт в сессии своего теста.

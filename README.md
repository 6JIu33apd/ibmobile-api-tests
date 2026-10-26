# ibmobile-api-tests

Контрактные API-тесты бэкенда розничного интернет-банка (ibMobile, REST/JSON).
Стек: Java 25, Maven, JUnit 5, Rest Assured, Allure, AssertJ, Jackson.

Что проверяем:
- **контракт** — каждый ответ сверяется со строгой JSON-схемой (лишнее поле — ошибка);
- **бизнес-логику** — ключевые значения ответа и бизнес-отказы проверок с точным текстом;
- **ошибки сервера** — HTTP не 200 или недопустимый errorCode роняет любой тест.

Чего не проверяем: остатки и движение денег (на dev деньги не двигаются), сквозные e2e-сценарии,
проверки «ради проверок» — мусорный ввод на каждое поле.

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
mvnw test                        # dev, текущий релиз
mvnw test -Denv=test             # контур: dev | test | prod
mvnw test -Drelease=3.19         # версии ручек из versions/3.19.properties
mvnw test -Dtest=AccListTest     # один класс
mvnw allure:serve                # отчёт
```

Из IDEA тесты запускаются как обычные JUnit-тесты; `-Denv` / `-Drelease` — в VM options.

## Контуры

| Контур | Что это | Логины | Потоки |
|---|---|---|---|
| dev | разработка, коды подтверждения фиксированные, деньги не двигаются | тестовые, мультисессия | 2 |
| test | клон прода | только свой логин, одна сессия | 1 |
| prod | боевой | только свой логин, одна сессия | 1 |

На prod запускаются только классы с `@Tag("prod")`, остальные пропускаются целиком (без логина).

## Структура

```
src/test/java/ru/avangard/qa/ibmobile/
  config/   Config, Profile, Threads          откуда берутся настройки, логины, число потоков
  api/      Endpoint, Request, ApiResponse,   ручки, запрос, проверки ответа,
            ErrorCode, Session                 допустимые ошибки, сессия (логин/логаут)
  core/     BaseTest, Login, Doc, Db           база тестов, документы, чтение БД
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
| `config/Config` | контур и релиз по умолчанию (`dev`, `3.20`) |
| `env/<env>.properties` | всё о контуре: baseUrl, потоки, коды подтверждения (dev), БД (test/prod) |
| `versions/<релиз>.properties` | версии ручек релиза |
| `~/.ibmobile/secrets.properties` | логины, пароли профилей и БД; ключи `<env>.*` |

Секреты текущего контура накладываются поверх `env/<env>.properties` без префикса: `dev.MAIN.login` → `MAIN.login`.
Пустое значение (`db.url=`) считается незаданным: обращение к нему роняет тест с понятным сообщением.

`uid` в каждом запросе — рудимент протокола, бэк принимает любой; это константа в `Request`.
Тайм-ауты запроса — соединение 10 с, ответ 60 с.

### Релизы и версии

Пути ручек версионированы: `Auth/ticket/9.4`, `Doc/prepare_doc/8.1`. В `Endpoint` путь без версии,
версия берётся из `versions/<релиз>.properties` по имени константы. Схема ищется по той же версии:
`schemas/acc_list/4.0.json`. Поэтому `-Drelease` переключает и пути, и схемы — старый сервер гоняется
теми же тестами. Новый релиз — копия файла версий с правкой изменившихся строк и новые схемы для них.

## Запрос и ответ

```java
Request.to(ACC_DETAILS).as(session()).param("account", account).post()
        .success()                     // не отказ
        .matchesSchema()               // строгая схема ручки
        .path("accBalanceDetails[0].account");
```

- `Request.to(endpoint)` — запрос; `uid` подставляется сам, `as(session)` добавляет `ticket` (form-параметр).
- Тело — URLENC; у ручек, помеченных в `Endpoint` как multipart (`prepare_doc` и немногие другие), — MULTIPART.
- Коллекция в `param` — несколько одноимённых параметров (например, два `secure` в `send_doc`).
- Каждый запрос — шаг Allure с телом запроса и ответа.

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
- Схемы ошибок в `schemas/errors/` написаны руками.

## Сессии и профили

- Все классы наследуют `BaseTest`. `session()` — одна сессия на класс: логин при первом обращении, логаут после класса.
- Профиль по умолчанию — `MAIN`. Другой профиль — аннотация `@Login(Profile.X)` на классе.
- Профиль — enum `config/Profile`, логин и пароль — в секретах. Нет профиля на контуре — класс пропускается.
- Тесты, которые разлогинивают (логаут, смена логина или пароля), открывают свою сессию:

```java
try (var s = Session.login(MAIN)) {
    s.logout().matchesSchema();
}
```

- Неверный пароль реального профиля **не тестируем**: 5 попыток — блокировка без сброса.
  Негатив логина — только несуществующий логин; одноразовые профили для блокировок будут отдельно.

## Документы

Документ — всё, что меняет данные. У каждого есть `doc_type`, путь всегда один:

```
prepare_doc (doc_type, doc_content)  →  подтверждение способом из authMethods  →  send_doc (secure)
```

| Способ | Что делает `Doc.send` |
|---|---|
| `NONE` | безобидные документы (перевод между своими счетами с дебетового, заявление, снижение лимита); токен — `noneAuthInit.code` |
| `SMSPWD` | `send_confirm_sms`, затем два `secure`: код SMS и пароль. Код на dev — из `env/dev.properties`, на test/prod — из БД |
| `SCRETCH` | `show_scretch`, затем код скретч-карты (только dev) |

- Способ задаётся в кейсе заранее, `Doc.send` вызывает только его. Ручки подтверждения тестируются своими классами.
- Подготовленный документ живёт в сессии 5 минут, поэтому классы с документами — `@Execution(SAME_THREAD)`.
- Суммы — только `BigDecimal` (`ApiResponse.decimal`): double даёт `1.5699999999999998`, бэк такой документ отклоняет.
- «Подобный документ уже обрабатывается в банке.» на `send_doc` — плановый бизнес-отказ, защита клиента от дубля.
  Поэтому суммы в позитивных кейсах случайные (1.01–9.99).
- Коды подтверждения не подбирать: попытки ограничены.

### doc_content и параметризация кейсов

`templates/<doc_type>.json` — шаблон тела документа (копия из Postman). `Doc.content(DOC)` даёт его как Jackson `ObjectNode`.
В тестовом классе есть валидный вариант по умолчанию, кейс меняет в нём поля. JSON на каждый кейс не нужен.

```java
private static ObjectNode content(String accDeb, String accCred, BigDecimal amount) {
    return Doc.content(DOC).put("accDeb", accDeb).put("accCred", accCred).put("amount", amount);
}
```

Кейсы, которые меняют одно поле, — строки таблицы `@MethodSource`. `Doc.set(content, поле, значение)` меняет поле,
`null` — убирает его; значение — строка, число, список или Map:

```java
static Stream<Arguments> refusals() {
    return Stream.of(
            arguments("пустой текст", "text", "", "text", "Заполните текст заявления"),
            arguments("без текста", "text", null, "text", "Заполните текст"));
}

@Story("Негативные")
@DisplayName("Заявление отклоняется проверками")
@ParameterizedTest(name = "({0})")
@MethodSource("refusals")
void refusal(String title, String field, Object value, String badItem, String message) {
    Doc.prepare(session(), DOC, Doc.set(content(TEXT), field, value)).rejected(badItem, message);
}
```

Кейс со сложной логикой (несколько полей, свои проверки) — отдельный метод.

Файлы из `files/` прикладываются аргументами `Doc.prepare` и сами попадают в `attachmentsList`:

```java
var prepared = Doc.prepare(session(), DOC, content(TEXT), "image.png").success().matchesSchema(DOC);
Doc.send(session(), prepared, Confirm.NONE).success().matchesSchema(DOC);
```

Схема документа — вариант схемы ручки: `matchesSchema(DOC)` → `schemas/prepare_doc/<doc_type>/8.1.json`.

## Данные из других ручек

Тестовые данные (номера счетов и т. п.) берутся из ответов других ручек, а не хранятся в репозитории.
Класс `data/<Ручка>Data` хранит ответ ручки **целиком** и загружается один раз на тестовый класс:

```java
@BeforeAll
void accounts() {
    accList = new AccListData(session());
}

accList.accounts(AccountType.DEBIT);         // номера рублёвых дебетовых счетов
accList.sameCurrency(account);               // другие счета в валюте account
accList.path("accounts.account.currency");   // любое поле — по пути
```

Нужен новый срез — путь прямо в тесте; повторяется в нескольких тестах — метод в классе `Data`.

Счета (`AccountType`):

| Тип | Признак в acc_list | Особенности |
|---|---|---|
| дебетовый (`DEBIT`, `DEBIT_FX`) | `cardAcc`, не `credit` | могут быть карты |
| кредитный (`CREDIT`, `CREDIT_FX`) | `cardAcc`, `credit` | могут быть карты |
| текущий (`CURRENT`, `CURRENT_FX`) | `currAcc` | только дебетовый, без карт |
| депозитный (`DEPOSIT`) | `idepAcc` | дебетовый, без карт, своя логика |

`_FX` — валютный. Карта не существует без счёта и лишь прокси: списание всегда со счёта.
Выборки отсортированы по доступной сумме (`otb`) от большей к меньшей.

## Отчёт Allure

- `@Epic` — группа функций из `tests/Section` (Счета, Платежи, Заявления, Аэроэкспресс, Настройки…).
- `@Feature` — ручка или документ.
- `@Story("Негативные")` — негативные кейсы внутри фичи.
- Имя кейса — `@DisplayName` метода + изменяемая часть из `@ParameterizedTest(name = ...)`.
- Результаты прошлых прогонов очищаются перед каждым запуском.

## Новая ручка

1. Константа в `api/Endpoint` (путь без версии, название для отчёта, `true` — MULTIPART) и версия в `versions/*.properties`.
2. Класс `tests/<домен>/<Ручка>Test extends BaseTest` с `@Epic` и `@Feature`. Кейсы — параметры.
3. Позитивные кейсы: `success()`, `matchesSchema()`, проверка ключевых значений.
4. Негативные кейсы — обязательно, `@Story("Негативные")`: бизнес-отказы `rejected(...)`, технические `error(...)`.
5. Документы — `@Tag("mutating")`, `@Execution(SAME_THREAD)`, шаблон в `templates/`.
6. Прогнать: без схемы тест упадёт и сохранит ответ → `tools.SchemaDraft` → проверить схему → прогнать снова.
7. Для prod — `@Tag("prod")` и только то, что безопасно под своим логином.

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

JUnit гоняет классы и кейсы параллельно, число потоков — `threads` контура (`config/Threads`).
Сессия класса общая для его кейсов. Классы с документами — `SAME_THREAD`: документ и защита от дублей живут в сессии.

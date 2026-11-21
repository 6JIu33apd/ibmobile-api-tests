# ibmobile-api-tests

Контрактные API-тесты бэкенда розничного интернет-банка (ibMobile, REST/JSON).
Стек: Java 25, Maven, JUnit 6, Rest Assured 6, Allure 3, AssertJ, Jackson.

Что проверяем:
- **контракт** — ответы ручек сверяются со строгими JSON-схемами (лишнее поле — ошибка);
- **бизнес-логику** — ключевые значения ответа и бизнес-отказы проверок с точным текстом;
- **ошибки сервера** — фильтр требует HTTP 200, тест сверяет тело ответа с ожидаемым результатом.

Чего не проверяем: остатки, сквозные e2e-сценарии, мусорный ввод на каждое поле.

## Быстрый старт

1. JDK 25. Maven ставить не нужно — в репозитории есть `mvnw`.
2. Файл `~/.ibmobile/dev.properties` (для других контуров — `preprod.properties` и `prod.properties`, всё вне репозитория):

```properties
baseurl=http://www.avangard.ru/ibMobile_develop/REST
release=3.20
login=...
loginextra=...
password=...
sms=...
scretch=...
```

3. Из корня проекта в PowerShell используйте `.\mvnw.cmd`, в Git Bash — `./mvnw`.

```powershell
.\mvnw.cmd test                            # тесты dev (без @PreProd / @Prod)
.\mvnw.cmd test -Dtest=AccListTest         # один класс
.\mvnw.cmd test -Pall-envs                 # ещё и тесты preprod и prod, каждый на своём контуре
.\mvnw.cmd test -Pall-envs -Dgroups=prod   # только тесты prod
.\mvnw.cmd allure:serve                    # отчёт
```

Из IDEA тесты запускаются как обычные JUnit-тесты, фильтр pom там не действует: запущенный тест идёт на свой контур.

## Контуры

Контур задаётся у теста, а не у прогона: по умолчанию dev, `@PreProd` / `@Prod` на методе или классе — preprod / prod.
Тест запускается на контуре с нужной версией.
Аннотации вложены в `BaseTest`; для аннотации на классе нужен импорт `BaseTest.PreProd` или `BaseTest.Prod`.
Выбор делает `BaseTest` перед кейсом; в `Config` контура по умолчанию нет. Вне `BaseTest` нужны `Config.setEnv(...)` и `Session.open(ключ)`.

| Контур | Аннотация | Логины | Параллельно |
|---|---|---|---|
| dev | — | тестовые, мультисессия | 2 потока (`junit-platform.properties`) |
| preprod | `@PreProd` | только свой логин, одна сессия | по одному (`@ResourceLock("preprod")`) |
| prod | `@Prod` | только свой логин, одна сессия | по одному (`@ResourceLock("prod")`) |

`@PreProd` / `@Prod` — ещё и теги `preprod` / `prod`. В pom по умолчанию `excludedGroups=preprod,prod`: обычный прогон
(и будущий CI) не трогает preprod и prod; профиль `-Pall-envs` снимает фильтр.

## Структура

```
src/test/java/ru/avangard/qa/ibmobile/
  config/   Config                            настройки контура и логины
  api/      Endpoint, DocType, HttpFilter,    ручки, типы документов, транспорт, проверки ответа,
            ResponseCheck, ErrorCode            допустимые ошибки
  core/     BaseTest (Login, PreProd, Prod, Negative) база тестов и вложенные аннотации
            Session                             сессия кейса
            DocContent, Confirm, Db             тело документа, подтверждение и чтение БД
  tests/    Section, <домен>/<Ручка>Test       тесты; Section — группы в Allure
  tools/    SchemaDraft                        черновики JSON-схем из реальных ответов
src/test/resources/
  versions/<релиз>.properties                  версии ручек релиза
  schemas/<ручка>/[<doc_type>/]<версия>.json   схемы ответов
  schemas/errors/                              схемы ошибок
  doc_content/<doc_type>.json                  шаблоны doc_content
  files/                                       файлы-вложения
  junit-platform.properties                    параллельный запуск JUnit
```

Настройки — в `config`, протокол — в `api`, жизненный цикл теста и документы — в `core`, кейсы — в `tests`.
`SchemaDraft` объединяет сбор образцов и генерацию черновиков схем.

## Конфиг

| Где | Что |
|---|---|
| `~/.ibmobile/<env>.properties` | baseurl, release, логины, общий пароль и коды; при необходимости — настройки БД |
| `versions/<релиз>.properties` | версии ручек релиза |

`Config.get("password")` читает ключ текущего контура без префиксов и преобразований.
`Config` кэширует файлы контуров, `Endpoint` — файлы версий API по релизу. Общий `Config.load` только читает указанный источник в UTF-8.
Каждый используемый файл загружается один раз за прогон; дополнительные логины берутся из кэша. Неиспользуемые контуры не загружаются.
`Config.get` читает обязательное значение из закэшированных настроек текущего контура.
Отсутствие файла контура — ошибка конфигурации. Старый `secrets.properties` не используется.
Пустые и пробельные значения удаляются при загрузке; обращение к обязательному отсутствующему ключу роняет тест.
Значения не обрезаются, чтобы сохранять значимые пробелы в паролях.

`uid` в каждом запросе — рудимент протокола, бэк принимает любой; его добавляет `HttpFilter`.
Тайм-ауты запроса — соединение 10 с, ответ 2 мин.

### Релизы и версии

Пути ручек версионированы: `Auth/ticket/9.4`, `Doc/prepare_doc/8.1`. В `Endpoint` путь без версии,
версия берётся из `versions/<release>.properties` по имени константы, `release` — из `~/.ibmobile/<env>.properties`
контура теста. Схема ищется по той же версии: `schemas/acc_list/4.0.json`. Поэтому `release` переключает и пути,
и схемы — у каждого контура свой релиз. Новый релиз — копия файла версий с правкой изменившихся строк, новые схемы
для них и `release=` в файле контура.
Путь ручки и версия начинаются с `/`. Например, `Endpoint.path()` возвращает `/Auth/ticket/9.4` и совпадает с путём запроса в Rest Assured.

## Запрос и ответ

```java
Response response = given().formParam("account", account).post(ACC_DETAILS.path());
matchesSchema(response, ACC_DETAILS.schema());
String actualAccount = response.path("accBalanceDetails[0].account");
```

- `given`, `formParam`, `post`/`delete` — нативный Rest Assured; проверки — статические методы `ResponseCheck`.
- `HttpFilter` добавляет `uid`, ticket и base URL, записывает HTTP-обмен и требует статус 200.
  Явный `formParam("ticket", ...)` сохраняется. Запрос логина и `given().auth().none()` автоматический ticket не получают.
- `null` в `formParam` не означает пропуск: в таблицах — `formParams(value == null ? Map.of() : Map.of(name, value))`.
- Тип тела задан в `Endpoint`. `HttpFilter` применяет его при отправке. Для `prepare_doc` это multipart, для остальных текущих ручек — URLENC.
- В URLENC коллекция в `formParam` передаётся как несколько одноимённых параметров (например, два `secure` в `send_doc`).
- Каждый запрос — шаг Allure с телом запроса и ответа; у multipart отдельное вложение «Параметры запроса»:
  карта «ключ=значение»; файлы описаны в `doc_content.attachmentsList`.
- Дробные числа ответа — `BigDecimal` (Rest Assured по умолчанию читает их как `Float` с потерей точности).

### Ошибки

При HTTP 200 фильтр возвращает ответ тесту, в том числе при `errorCode=2`. Проверку выбирает тест по ожидаемому результату.

| Вид | Как выглядит | Проверка |
|---|---|---|
| Бизнес-отказ | `responseCode=-1`, `resultCode=FAILED`, `checkResult` | `rejected(response, badItem, errorMessage)` сверяет причину отказа и проверяет схему. Когда важен только формат отказа, достаточно `matchesSchema(response, "schemas/errors/check_failed.json")` |
| Техническая ошибка | `errorCode` и `errorDesc` | `error(response, ErrorCode.X)` сверяет оба поля с ожидаемой ошибкой и проверяет `schemas/errors/error.json` |
| Ошибка HTTP | Статус отличается от 200 | `HttpFilter` завершает HTTP-шаг с ошибкой |

Допустимые технические ошибки — только из `ErrorCode`:

| errorCode | errorDesc | Когда |
|---|---|---|
| 1 | authorization_required | нет ticket |
| 4 | invalid_parameters | некорректные или отсутствующие параметры |
| 5 | session_expired | ticket после логаута |
| 1000 | Некорректный логин или пароль пользователя. | неверный логин |

## Схемы

- Строгие, JSON Schema draft-04, без `$ref`: структуры встроены в схему. `additionalProperties: false`; обязательные поля в `required`, остальные — опциональные.
- Подтверждённые значения статусов закреплены в схемах; `success(response)` отсекает отказы там, где схема не проверяется.
- Один `endpoint.schema(DocType...)`: `schema()` для обычных ручек, `schema(docType)` для prepare_doc/prior_info/send_doc. `matchesSchema(response, path)` проверяет схему; отсутствие схемы роняет тест.
- `SchemaDraft` включён по умолчанию и собирает успешные JSON-объекты и массивы при запуске одного теста, группы или всего набора, даже при наличии утверждённой схемы.
  Ответ сохраняется в `.schema-samples/schemas/<endpoint>/[<doc_type>/]<uuid>.json`, накопленная схема — в `.schema-samples/drafts/schemas/` по тому же пути и с тем же UUID.
  Подкаталог задаёт `doc_type` текущего запроса. В `send_doc` передаётся `secure` без `doc_type`, поэтому образцы лежат прямо в `schemas/send_doc/`. Одинаковые тела получают один UUID.
  Генератор работает молча: не пишет в Allure и не роняет тест при сбое. Каталог `.schema-samples` не попадает в git и переживает `mvn clean`.
- Обязательны поля, присутствующие во всех накопленных образцах, включая вложенные объекты и элементы массивов. По одному образцу опциональность не определить; `null` считается присутствующим значением.
  Образец `{}` делает поля корневого объекта опциональными в черновике. Пустое HTTP-тело, ошибки и отказы пропускаются. Структура массива уточняется, когда появляется образец с элементами.
- `-DschemaDraft.skip=true` отключает сохранение ответов и обновление схем. Подготовка, отправка и проверка запроса от этого флага не зависят.
- Нужный снимок `<uuid>.json` автор тестов проверяет и вручную переносит в `src/test/resources/schemas` под именем `<версия>.json`. Генератор эталоны не меняет; контрактные проверки используют только их.
- Ручная `schemas/errors/error.json` требует `errorCode` и `errorDesc`; `resultCode=FAILED` и `resultMessage` опциональны.
  Бизнес-отказы — ручная `check_failed.json`; описание элементов `checkResult` встроено в схемы документов.

## Сессии и логины

Одна сессия — на один тест: одна ручка разным пользователям отдаёт разные данные.

- Все классы наследуют `BaseTest`: после выбора контура он вызывает `Session.open(ключ)`, выполняя вход и заменяя сессию в ThreadLocal.
  Вход выполняется до тела теста и отображается отдельным шагом Allure; запросы кейса используют готовый ticket. После кейса `Session.close()` выполняет логаут только при успешном входе.
  Ошибка автоматического логаута не роняет тест; HTTP-запрос остаётся в Allure. Контекст сессии и контура очищается в любом случае.
- По умолчанию ключ — `login`. `@Login("loginextra")` на методе выбирает другой ключ того же файла.
  В аннотации указывается имя ключа, а не реальный логин; новые ключи не требуют enum или правок конфигурационного кода.
- `password`, `sms`, `scretch` общие для всех логинов контура. Коды требуются только для выбранного способа подтверждения.
  Логин и остальные нужные значения обязательны; ошибка авторизации роняет тест, автоматического логаута после неё нет.
- `given().auth().none()` отключает автоматический ticket только для этого запроса: так пишутся публичные запросы и негативы без ticket.
- `Session.current().loginResponse()` возвращает сохранённый ответ входа, `ticket()` — текущий ticket без запроса; явный `logout()` выполняет выход со строгой проверкой. После выхода ticket не обновляется.
- `mvnw clean test` удаляет `target` перед прогоном; накопленные ответы и готовые схемы в `.schema-samples` сохраняются.
- Неверный пароль реальных профилей не тестируем (блокировка); негатив логина — несуществующий логин.
- Смена логина/пароля тестируется отдельными логинами: ticket становится недействительным, исходные данные восстанавливает сам тест.

## Документы

prepare_doc вызывается через нативный `given()`, отправка — через `Confirm.send`.
Тип документа — константа enum `api/DocType`; в тестовом классе — `private static final DocType DOC_TYPE = DocType.X`.
Значение `doc_type` — имя константы в нижнем регистре; по нему же ищутся шаблон и схемы документа.
`DocContent` готовит тело и вложения, `Confirm.send` подтверждает и отправляет документ выбранным в тесте способом.
Вызов send_doc добавляется явно в нужный позитив; отдельного контрактного класса для этой ручки пока нет.

```java
var request = given().formParam("doc_type", DOC_TYPE.toString());
var docContent = DocContent.load(DOC_TYPE);
Response prepared = request.formParam("doc_content", docContent.toString()).post(PREPARE_DOC.path());
matchesSchema(prepared, PREPARE_DOC.schema(DOC_TYPE));
success(Confirm.send("NONE", prepared));
```

| Способ | Подтверждение перед send_doc в `Confirm.send(method, prepared)` |
|---|---|
| `NONE` | `secure` с токеном `noneAuthInit.code` |
| `SMSPWD` | `check_password/1.1` с паролем входа → `send_confirm_sms/1.1` → два `secure`: SMS с кодом `sms`, PWD с паролем |
| `SCRETCH` | `show_scretch`, затем `secure` с кодом `scretch` |

- Способ задаётся в кейсе строкой `"NONE"`, `"SMSPWD"` или `"SCRETCH"`; `Confirm.send` возвращает ответ send_doc.
  `Session` отвечает за вход, ticket и логаут.
  Токены — JSON-строки, повторные `secure` кодирует Rest Assured.
- Коды читаются из файла текущего контура только для выбранного способа; `NONE` их не требует.
- Суммы — только `BigDecimal`: `response.jsonPath().getObject("doc.amount", BigDecimal.class)`; double даёт `1.5699999999999998`.

### doc_content и параметризация кейсов

`DocContent.load(DOC_TYPE)` читает тело из `doc_content/<doc_type>.json` (копия из Postman) как Jackson `ObjectNode`.
В multipart `doc_content` передаётся строкой через `.toString()`: Rest Assured не сериализует ObjectNode с этим Content-Type.
Шаблон содержит данные по умолчанию; тест подставляет динамические значения и меняет нужные поля. JSON на каждый кейс не нужен.

```java
ObjectNode docContent = DocContent.load(DOC_TYPE);
```

Параметры задаются через `@FieldSource` и `Arguments[]`. Имя кейса указывается в `argumentSet`, набор полей формы можно передать как `Map`.
`DocContent.set(docContent, путь, значение)` меняет поле, `null` — убирает его; значение — строка, число, список или Map.
Путь — через точку на любую глубину, индекс массива — число: `"amount"`, `"payer.address.city"`, `"attachmentsList.0.filename"`.
Списки и Map остаются JSON-деревом, доступным по вложенным путям.

```java
static final Arguments[] REJECTIONS = {
    argumentSet("пустой текст", "text", "", "text", "Заполните текст заявления"),
    argumentSet("без текста", "text", null, "text", "Заполните текст")
};

@ParameterizedTest(name = "Заявление отклоняется проверками ({argumentSetName})")
@FieldSource("REJECTIONS")
void rejection(String field, Object value, String badItem, String errorMessage) {
    DocContent.set(docContent, field, value);
    var request = given().formParam("doc_type", DOC_TYPE.toString());
    rejected(request.formParam("doc_content", docContent.toString()).post(PREPARE_DOC.path()), badItem, errorMessage);
}
```

Кейс со сложной логикой (несколько полей, свои проверки) — отдельный метод. Для простых URLENC-ручек достаточно явных параметров;
отдельные шаблоны с парой полей не нужны. Шаблоном ответа служит JSON Schema, эталонный JSON не хранится.

`DocContent.attach` добавляет файлы из classpath `files/` в запрос и обновляет `attachmentsList`. Без файлов исходное описание сохраняется.
Добавляйте `doc_content` в форму после `attach`, чтобы в запрос попал окончательный список вложений.

```java
ObjectNode docContent = DocContent.load(DOC_TYPE);
var request = given().formParam("doc_type", DOC_TYPE.toString());
DocContent.attach(request, docContent, "image.png");
Response response = request.formParam("doc_content", docContent.toString()).post(PREPARE_DOC.path());
matchesSchema(response, PREPARE_DOC.schema(DOC_TYPE));
```

Схема документа — вариант схемы ручки: `matchesSchema(response, PREPARE_DOC.schema(DOC_TYPE))` → `schemas/prepare_doc/<doc_type>/8.1.json`.

## Данные из других ручек

Тестовые данные (номера счетов и т. п.) берутся из ответов других ручек, а не хранятся в репозитории.
Ответ помощника хранится в локальном `JsonPath` или в поле экземпляра, если общий запрос выполняется в `@BeforeEach`. В обоих случаях HTTP-шаг виден в Allure.

```java
var accList = success(given().post(ACC_LIST.path()));
String account = find(accList, "accounts.account.findAll { it.cardAcc && !it.isCredit }.max { it.otb }?.code");
List<?> accounts = accList.get("accounts.account");
```

Выборка — полный GPath (Groovy) из теста: условия и нужное поле.
`BaseTest.find(response, path)` пропускает кейс при `null`; `response.get(path)` позволяет обработать `null` в тесте. Ошибки GPath роняют тест.

Успех помощника проверяется явным `success(...)` в тесте. Схема проверяется у целевой ручки.
Отдельных классов данных и общих таблиц кейсов нет.

## Отчёт Allure

- `@Epic` — группа функций из `tests/Section` (Счета, Платежи, Заявления, Аэроэкспресс, Настройки…).
- `@Feature` — ручка или документ.
- Story «Негативные» — негативные кейсы внутри фичи: вложенный класс `@Nested @Negative class Negatives`.
- Имя кейса — русское имя JUnit: `@DisplayName` либо `name` у `@ParameterizedTest` с `{argumentSetName}` / `{0}`.
- Maven очищает `target/allure-results` на фазе `initialize` перед тестами.

## Тестовый класс

Все классы устроены одинаково: нативный `given()`, явные параметры, `.post(endpoint.path())`, затем отдельные проверки ответа.
В классе — данные по умолчанию и кейсы, обёрток над запросом нет.
У `prepare_doc` — класс на `doc_type`; send_doc используется только как вспомогательный запрос для отправки документа.
`AuthTicketTest` объединяет связанные контракты логина и логаута.

| Порядок | Что | Ручка (`AccDetailsTest`) | Документ (`IbOurAccTransferTest`) |
|---|---|---|---|
| 1 | позитивные кейсы | `details(condition)` | `transfer(accDebCondition, authMethod)` |
| 2 | `@Nested @Negative class Negatives` → `error(response, INVALID_PARAMETERS)` | `invalidAccount(params)` с пустой картой или некорректным `account` | `invalidParameters(amount)` |
| 3 | таблица `rejections` → `rejected(response, badItem, errorMessage)` | — | поле документа → значение |
| 4 | особые кейсы — отдельные методы | — | `sameAccount()` |
| 5 | `withoutTicket`: запрос с `.auth().none()` → `error(response, AUTHORIZATION_REQUIRED)` | `withoutTicket()` | `withoutTicket()` |
| 6 | данные по умолчанию | условие выбора счёта | шаблон и счета из `acc_list` |

Кейс таблицы меняет одно значение в валидном запросе, `null` — убирает его:
у ручки — `.formParams(value == null ? Map.of() : Map.of(name, value))`, у документа — `DocContent.set(docContent, field, value)`.
Имена — как у бэка: `docType`, `docContent`, `authMethod`, `accDeb`, `badItem`, `errorMessage`; без сокращений.

## Новая ручка

1. В `api/Endpoint` укажите базовый путь с начальным `/`, HTTP-метод и тип тела. В `versions/<релиз>.properties` добавьте версию с начальным `/`.
2. Класс `tests/<домен>/<Ручка>Test extends BaseTest` с `@Epic` и `@Feature`. Кейсы — параметры.
3. Позитивные кейсы: `matchesSchema(response, endpoint.schema())` (для документа — `schema(DOC_TYPE)`), проверка ключевых значений.
4. Негативные кейсы — обязательно, во вложенном `@Nested @Negative class Negatives`: бизнес-отказы `rejected(...)`,
   технические `error(...)`. Аннотация даёт Story «Негативные» всем его тестам.
5. Документы — константа в `api/DocType`, шаблон `doc_content/<doc_type>.json`, в классе `DOC_TYPE`
   и `@ResourceLock("prepared_doc")` на классе.
6. Запустите кейс с включённым сбором. На успешном ответе черновик сохраняется до вызова `matchesSchema`. Пока утверждённой схемы нет в ресурсах, эта проверка падает при каждом запуске. Проверьте черновик, перенесите его в ресурсы под именем версии и повторите тест.
7. Не dev — `@PreProd` / `@Prod`; на prod только то, что безопасно под своим логином.

```java
@Epic(Section.ACCOUNTS)
@Feature("Список счетов")
class AccListTest extends BaseTest {

    @DisplayName("Счета клиента с уникальными 20-значными номерами")
    @Test
    void accList() {
        Response response = given().post(ACC_LIST.path());
        matchesSchema(response, ACC_LIST.schema());
        assertThat(response.<List<String>>path("accounts.account.code")).isNotEmpty().doesNotHaveDuplicates();
    }

    @Nested
    @Negative
    class Negatives {

        @DisplayName("Запрос без ticket отклоняется")
        @Test
        void withoutTicket() {
            error(given().auth().none().post(ACC_LIST.path()), AUTHORIZATION_REQUIRED);
        }
    }
}
```

## Параллельность

Классы (ручки) идут параллельно в 2 потока, кейсы одного класса, включая `Negatives`, — по очереди
(`junit-platform.properties`). Тесты preprod и prod — по одному на контур (`@ResourceLock` в `@PreProd` / `@Prod`),
параллельно с dev. Контур — на поток теста. У каждого теста свой экземпляр класса и своя сессия.
Классы документов — ещё и по одному между собой (`@ResourceLock("prepared_doc")`): все они вызывают prepare_doc,
а подготовленный документ бэк хранит общим для всех ticket логина.

`BaseTest` один раз задаёт общие `RestAssured.config` и фильтр в статическом блоке; base URL выбирается фильтром для каждого запроса.

Тест, который меняет настройку клиента (например, лимит по счёту), возвращает её назад, даже если упал:
иначе пропадёт образец для следующих прогонов.

# ibmobile-api-tests

Контрактные API-тесты бэкенда интернет-банка: Java 25, Maven, JUnit 5, Rest Assured, Allure.

## Запуск

```
mvnw test                        # dev, текущий релиз
mvnw test -Denv=test             # контур: dev | test | prod
mvnw test -Drelease=3.19         # версии ручек из versions/3.19.properties
mvnw test -Dtest=AccListTest     # один класс
mvnw allure:serve                # отчёт
```

Контур и релиз по умолчанию — в начале `config/Config`. На prod запускаются только классы с `@Tag("prod")`.

## Структура

```
config/   Config, Profile, Threads          откуда берутся настройки, логины, число потоков
api/      Endpoint, Request, ApiResponse,   ручки, запрос, проверки ответа, допустимые ошибки,
          ErrorCode, Session                 сессия (логин/логаут)
core/     BaseTest, Login, Doc, Db           база тестов, документы, чтение БД
data/     <Ручка>Data                        данные из ответов ручек для других тестов
tests/    Section, <домен>/<Ручка>Test       тесты; Section — группы в Allure
tools/    SchemaDraft                        черновики JSON-схем
```

## Конфиг

| Где | Что |
|---|---|
| `env/<env>.properties` | всё о контуре: baseUrl, потоки, коды подтверждения, БД |
| `versions/<релиз>.properties` | версии ручек; новый релиз — копия файла с правкой изменившихся строк. Схема ищется по версии ручки: `-Drelease` переключает и пути, и схемы |
| `~/.ibmobile/secrets.properties` | профили `<env>.<профиль>.login/.password` (нет профиля — класс пропускается), `<env>.db.user/.password` |
| `junit-platform.properties` | параллельный запуск JUnit |

## Новая ручка

1. Константа в `api/Endpoint` (путь без версии, `true` — MULTIPART) и версия в `versions/*.properties`.
2. Класс `tests/<домен>/<Ручка>Test`, кейсы — параметры. Все классы наследуют `BaseTest`; `session()` — сессия на класс
   под профилем `MAIN`, логин при первом обращении. Другой профиль — `@Login(PROFILE)` на классе:

```java
@Epic(Section.ACCOUNTS)
@Feature("Список счетов")
class AccListTest extends BaseTest {
    @Test
    void accList() {
        Request.to(ACC_LIST).as(session()).post().success().matchesSchema();
    }
}
```

   Классы с документами — `@Execution(SAME_THREAD)`: документ живёт в сессии.

   У каждой ручки есть негативные кейсы — `@Story("Негативные")`:
   - бизнес-ошибка — `rejected(badItem, сообщение)`: отказ проверок с полем-виновником и описанием;
   - техническая — `error(...)`: только errorCode 1, 4, 5, 1000 из `ErrorCode`.

   Сервер не должен отдавать 500: HTTP не 200 или errorCode вне `ErrorCode` роняет любой тест («Ошибка сервера: …»).

   Отчёт: `@Epic` — группа функций из `Section` (Счета, Платежи, Заявления, Аэроэкспресс, Настройки…),
   `@Feature` — ручка или документ, негативные — группа «Негативные» внутри.
   Имя кейса — что проверяется: `@DisplayName` на методе + `@ParameterizedTest(name = "...")` с изменяемой частью.
   Результаты прошлых прогонов очищаются перед каждым запуском.

   Данные из других ручек — `data/<Ручка>Data`: например, `AccListData.accounts(session(), AccountType.DEBIT)` —
   номера рублёвых дебетовых счетов из acc_list.

3. Схема: прогон без схемы падает и сохраняет ответ в `target/schema-samples` (`-Dsamples=true` — сохранять все ответы).
   `tools.SchemaDraft` делает черновик в `schemas/`: поле обязательно, если оно есть во всех образцах. Черновик проверить руками.

## Документы

Всё, что меняет данные: `prepare_doc` с `doc_type` → подтверждение способом из ответа → `send_doc`.
Способ подтверждения задаётся в кейсе заранее, `Doc.send` вызывает только его: `send_confirm_sms` для SMSPWD,
`show_scretch` для SCRETCH. Безобидные документы — `Confirm.NONE`. Сами ручки подтверждения — в своих классах.
`doc_content` — `Doc.content(DOC)` из `templates/<doc_type>.json`, в тесте меняются только нужные поля.
Файлы из `files/` прикладываются аргументами `Doc.prepare` и сами попадают в `attachmentsList`.

```java
var prepared = Doc.prepare(session(), DOC, Doc.content(DOC).put("text", text), "image.png").success().matchesSchema(DOC);
Doc.send(session(), prepared, Confirm.SMSPWD).success().matchesSchema(DOC);
```

- Суммы — `BigDecimal` (`ApiResponse.decimal`): double даёт `1.5699999999999998`, бэк такой документ отклоняет.
- Похожий документ (те же счета и сумма), пока обрабатывается предыдущий, бэк отклоняет как дубль:
  суммы случайные, кейсы класса — `@Execution(SAME_THREAD)`.

## Своя сессия

Тесты, которые разлогинивают (логаут, смена логина или пароля), работают на своих сессиях, без `session()`:

```java
try (var s = Session.login(MAIN)) {
    // смена пароля
}
try (var s = Session.login(MAIN.login(), newPassword)) {
    // проверка
}
```

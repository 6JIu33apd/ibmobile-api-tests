# ibmobile-api-tests

Контрактные API-тесты бэкенда интернет-банка: Java 25, Maven, JUnit 5, Rest Assured, Allure.

## Запуск

```
mvnw test                        # dev, релиз из config.properties
mvnw test -Denv=test             # контур: dev | test | prod
mvnw test -Drelease=3.19         # версии ручек из versions/3.19.properties
mvnw test -Dtest=AccListTest     # один класс
mvnw allure:serve                # отчёт
```

На prod запускаются только классы с `@Tag("prod")`. Потоков — `threads` контура (на test и prod 1: одна сессия на логин).

## Конфиг

| Файл | Что |
|---|---|
| `config.properties` | контур и релиз по умолчанию, общие параметры |
| `env/<env>.properties` | baseUrl, коды подтверждения, БД |
| `versions/<релиз>.properties` | версии ручек; новый релиз — копия файла с правкой изменившихся строк |
| `~/.ibmobile/secrets.properties` | профили `<env>.<профиль>.login/.password` (нет профиля — класс пропускается), `<env>.db.user/.password` |

## Новая ручка

1. Константа в `core/Endpoint` (`{v}` — версия, `true` — MULTIPART) и версия в `versions/*.properties`.
2. Класс `tests/<домен>/<Ручка>Test`, кейсы — параметры. Все классы наследуют `BaseTest`, с `@Login` — логин один раз на класс:

```java
@Epic(Section.ACCOUNTS)
@Feature("Список счетов")
@Login(MAIN)
class AccListTest extends BaseTest {
    @Test
    void accList() {
        Request.to(ACC_LIST).as(session).post().success().matchesSchema();
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

   Данные из других ручек — `data/`: например, `Accounts.of(session, Kind.DEBIT)` — номера рублёвых дебетовых счетов из acc_list.

3. Схема: прогон без схемы падает и сохраняет ответ в `target/schema-samples` (`-Dsamples=true` — сохранять все ответы).
   `tools.SchemaDraft` делает черновик в `schemas/`: поле обязательно, если оно есть во всех образцах. Черновик проверить руками.

## Документы

Всё, что меняет данные: `prepare_doc` с `doc_type` → подтверждение способом из ответа → `send_doc`.
Способ подтверждения задаётся в кейсе заранее, `Doc.send` вызывает только его: `send_confirm_sms` для SMSPWD,
`show_scretch` для SCRETCH. Безобидные документы — `Confirm.NONE`. Сами ручки подтверждения — в своих классах.
Файлы из `files/` прикладываются аргументами `Doc.prepare` и сами попадают в `attachmentsList`.

```java
var prepared = Doc.prepare(session, DOC, Template.of(DOC).with("text", text), "image.png").success().matchesSchema(DOC);
Doc.send(session, prepared, Confirm.SMSPWD).success().matchesSchema(DOC);
```

- Суммы — `BigDecimal` (`ApiResponse.decimal`): double даёт `1.5699999999999998`, бэк такой документ отклоняет.
- Похожий документ (те же счета и сумма), пока обрабатывается предыдущий, бэк отклоняет как дубль:
  суммы случайные, кейсы класса — `@Execution(SAME_THREAD)`.

## Своя сессия

Тесты, которые разлогинивают (логаут, смена логина или пароля), работают на своих сессиях — класс без `@Login`:

```java
try (var s = Session.login(MAIN)) {
    // смена пароля
}
try (var s = Session.login(MAIN.login(), newPassword)) {
    // проверка
}
```

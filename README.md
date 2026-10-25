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
2. Ручка с ticket — строка в `CommonAuthTest.endpoints()`.
3. Класс `tests/<домен>/<Ручка>Test`, кейсы — параметры. Логин один раз на класс:

```java
@Login(MAIN)
class AccListTest extends BaseTest {
    @Test
    void accList() {
        Request.to(ACC_LIST).as(session).post().success().matchesSchema();
    }
}
```

   Классы с документами — `@Execution(SAME_THREAD)`: документ живёт в сессии.

4. Схема: прогон без схемы падает и сохраняет ответ в `target/schema-samples` (`-Dsamples=true` — сохранять все ответы).
   `tools.SchemaDraft` делает черновик в `schemas/`: поле обязательно, если оно есть во всех образцах. Черновик проверить руками.

## Документы

Всё, что меняет данные: `prepare_doc` с `doc_type` → подтверждение способом из ответа → `send_doc`.
`Doc.send` сам дёргает `send_confirm_sms` (SMS) или `show_scretch` (скретч-карта). Безобидные документы — `Confirm.NONE`.

```java
var prepared = Doc.prepare(session, DOC, Template.of(DOC).with("amount", amount), "scan.jpg").success().matchesSchema(DOC);
Doc.send(session, prepared, Confirm.SMSPWD).success().matchesSchema(DOC);
```

- Суммы — `BigDecimal`: double даёт `1.5699999999999998`, бэк такой документ отклоняет.
- Похожий документ (те же счета и сумма), пока обрабатывается предыдущий, бэк отклоняет как дубль:
  суммы случайные, кейсы класса — `@Execution(SAME_THREAD)`.

## Своя сессия

Тесты, которые разлогинивают (логаут, смена логина или пароля), работают на своих сессиях, без `BaseTest`:

```java
try (var s = Session.login(MAIN)) {
    // смена пароля
}
try (var s = Session.login(MAIN.login(), newPassword)) {
    // проверка
}
```

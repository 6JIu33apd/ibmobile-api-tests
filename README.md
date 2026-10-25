# ibmobile-api-tests

Контрактные API-тесты бэкенда интернет-банка (Java 25, Maven, JUnit 5, Rest Assured, Allure).

## Запуск

```
mvnw test                                  # dev, текущий релиз
mvnw test -Denv=test                       # контур: dev | test | prod
mvnw test -Drelease=<номер релиза>         # версии ручек из versions/<номер релиза>.properties
mvnw test -Dtest=AccListTest               # один класс
mvnw allure:serve                          # открыть отчёт
```

На prod запускаются только тесты с `@Tag("prod")`. На test и prod тесты идут в один поток: там одна сессия на логин.

## Секреты

`~/.ibmobile/secrets.properties`, вне репозитория:

```
dev.<логин>.password=...
test.<логин>.password=...
test.db.user=...
test.db.password=...
```

## Где что лежит

| Что | Где |
|---|---|
| Контуры (baseUrl, uid, потоки, фиксированные коды, БД) | `src/test/resources/env/<env>.properties` |
| Логины пользователей по псевдонимам `User` | `src/test/resources/users/<env>.properties` |
| Версии ручек по релизам | `src/test/resources/versions/<релиз>.properties` |
| Схемы ответов | `src/test/resources/schemas/<ручка>/[<doc_type>/]<версия>.json` |
| Шаблоны `doc_content` | `src/test/resources/templates/<doc_type>.json` |
| Файлы для загрузки | `src/test/resources/files/` |

## Новая ручка

1. Константа в `api/Endpoint` (путь с `{v}`, если ручка версионная) и её версия в `versions/*.properties`.
2. Если ручка с ticket — строка в `tests/auth/CommonAuthTest.endpoints()`.
3. Класс `tests/<домен>/<Ручка>Test` с параметризованными кейсами:

```java
try (var s = Session.login(user)) {
    Request.to(ACC_LIST).as(s).param("account", acc).post()
            .success()
            .matchesSchema();
}
```

4. Схема: первый прогон без схемы упадёт и сохранит ответ в `target/schema-samples`.
   Для ответов под разными пользователями — прогон с `-Dsamples=true`.
   Затем запустить `tools.SchemaDraft` (из IDEA): он сделает черновик в `schemas/`.
   Поле обязательно, только если оно было во всех образцах. Черновик проверить и поправить руками.
   Существующие схемы генератор не трогает; чтобы пересоздать — удалить файл.

## Документы

`Doc/prior_info` → `Doc/prepare_doc` → `Doc/send_doc` в одной сессии (документ живёт 5 минут):

```java
var prepared = Request.to(PREPARE_DOC).as(s)
        .param("doc_type", DOC)
        .param("doc_content", Template.of(DOC).with("amount", 1.0))
        .file("scan.jpg")                       // если нужен файл
        .post().success().matchesSchema(DOC);

Request.to(SEND_DOC).as(s)
        .param("secure", Confirm.secure(s, prepared))
        .post().success().matchesSchema(DOC);
```

`Confirm` выбирает способ подтверждения из `authMethods` ответа `prepare_doc`.

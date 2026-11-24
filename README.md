# ibmobile-api-tests

Контрактные API-тесты ibMobile: строгие JSON-схемы ответов, ключевые бизнес-значения, бизнес-отказы и технические ошибки.

Стек: Java 25, Maven, JUnit 6, Rest Assured 6, Allure 3, AssertJ, Jackson.

## Быстрый старт

1. JDK 25, Maven Wrapper (`mvnw`).
2. Создайте `~/.ibmobile/dev.properties` (для других контуров — `preprod.properties` и `prod.properties`, вне репозитория):

```properties
baseurl=http://www.avangard.ru/ibMobile_develop/REST
release=3.20
login=...
loginextra=...
password=...
sms=...
scretch=...
```

3. Запуск из корня проекта в PowerShell (в Git Bash — `./mvnw`):

```powershell
.\mvnw.cmd test                           # dev
.\mvnw.cmd test -Dtest=AccListTest          # один класс
.\mvnw.cmd test -Pall-envs                  # все контуры
.\mvnw.cmd test -Pall-envs -Dgroups=prod    # только prod
.\mvnw.cmd allure:serve                    # отчёт
```

Из IDEA — обычный запуск JUnit; фильтр Maven не действует.

## Архитектура и структура

```text
src/test/java/ru/avangard/qa/ibmobile/
  config/   Config                         настройки контуров и логины
  api/      Endpoint, DocType, HttpFilter   метаданные API и транспорт
            ResponseCheck, ErrorCode       проверки ответов и ошибки
  core/     BaseTest, Session              жизненный цикл теста и сессии
            DocContent, SendDoc, Db        документы, подтверждение, чтение БД
  tests/    Section, <домен>/<Ручка>Test     тесты и группы Allure
  tools/    SchemaDraft                    генерация черновиков схем
src/test/resources/
  versions/<релиз>.properties               версии ручек
  schemas/<ручка>/[<doc_type>/]<версия>.json схемы ответов
  schemas/errors/                          схемы ошибок
  doc_content/<doc_type>.json               шаблоны документов
  files/                                   вложения
  junit-platform.properties                 параллельный запуск
```

Тесты используют нативный Rest Assured без обёрток над запросами.
`BaseTest` выбирает контур и открывает сессию; 
`HttpFilter` добавляет base URL, uid, ticket, устанавливает Content-Type и отправляет данные в Allure. 
`prepare_doc` — multipart, остальные текущие ручки — urlenc. 
`Config` кэширует настройки контуров, `Endpoint` — версии API по релизу. 
`release` определяет пути ручек и схемы ответов. 
Новый релиз — копия файла версий с изменениями, новые схемы и `release=` в конфигурации контура.
Тайм-ауты: соединение 10 с, ответ 2 мин.

### Контуры и сессии

| Контур | Аннотация | Логины | Параллельность |
|---|---|---|---|
| dev | — | тестовые, мультисессия | 2 потока |
| preprod | `@PreProd` | только свой логин | по одному |
| prod | `@Prod` | только свой логин | по одному |

Контур задаётся на методе или классе, по умолчанию dev. 
Maven исключает теги `preprod` и `prod`, профиль `-Pall-envs` снимает фильтр.
Одна сессия на тест, контур и сессия изолированы через `ThreadLocal`. 
Вход и логаут автоматические, контекст очищается после кейса. 
`@Login("loginextra")` выбирает ключ логина из конфигурации. 
`given().auth().none()` отключает добавление тикета к запросу, но не отменяет аутентификацию.

Классы выполняются параллельно, кейсы внутри класса — последовательно. 
Документы сериализуются через `@ResourceLock("prepared_doc")`: подготовленный документ общий для всех ticket логина. 
Изменённые настройки клиента тест восстанавливает даже при падении.

## Проверки и схемы

| Проверка | Назначение |
|---|---|
| `matchesSchema(response, endpoint.schema())` | контракт ответа |
| `success(response)` | отсутствие признаков отказа |
| `error(response, ErrorCode.X)` | техническая ошибка: код, описание и схема |
| `rejected(response, badItem, errorMessage)` | бизнес-отказ: причина и схема |

JSON Schema draft-04, `additionalProperties: false`, обязательные поля в `required`. 
Для документов — `endpoint.schema(DOC_TYPE)`. Отсутствие утверждённой схемы роняет тест.

`SchemaDraft` собирает успешные ответы в `.schema-samples/schemas/`, черновики — в `.schema-samples/drafts/schemas/`. 
Обязательны поля, присутствующие во всех образцах. 
Проверенный черновик вручную переносится в `src/test/resources/schemas` под именем `<версия>.json`. 
Генератор не меняет эталоны. `-DschemaDraft.skip=true` отключает сбор.

## Новая ручка

1. Добавьте в `Endpoint` базовый путь, HTTP-метод и Content-Type, в `versions/<релиз>.properties` — версию.
2. Создайте `tests/<категория>/<Ручка>Test extends BaseTest` с `@Epic` и `@Feature`.
3. Добавьте позитивные и негативные кейсы.
4. Запустите позитивный кейс, проверьте черновик схемы, перенесите его в ресурсы и повторите тест.
5. Для документа добавьте `DocType`, шаблон `doc_content/<doc_type>.json`, константу `DOC_TYPE` и `@ResourceLock("prepared_doc")` на классе.
6. Для других контуров — `@PreProd` / `@Prod`; на prod только безопасные тесты под своим логином.

## Новый тест

```java
@Epic(Section.ACCOUNTS)
@Feature("Список счетов")
class AccListTest extends BaseTest {
    @DisplayName("Список содержит уникальные номера счетов")
    @Test
    void accList() {
        Response response = given().post(ACC_LIST.path());
        matchesSchema(response, ACC_LIST.schema());
        assertThat(response.<List<String>>path("accounts.account.code")).isNotEmpty().doesNotHaveDuplicates();
    }
}
```
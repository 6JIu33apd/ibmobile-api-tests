package ru.avangard.qa.ibmobile.tests.doc;

import com.fasterxml.jackson.databind.JsonNode;
import io.restassured.specification.RequestSpecification;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import ru.avangard.qa.ibmobile.api.*;
import ru.avangard.qa.ibmobile.core.*;
import ru.avangard.qa.ibmobile.tests.Section;

import java.util.*;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static ru.avangard.qa.ibmobile.api.Endpoint.*;
import static ru.avangard.qa.ibmobile.api.ResponseCheck.*;

@ResourceLock("prepared_doc")
@Epic(Section.STATEMENTS)
@Feature("Заявление о несогласии с операцией по карте")
class IbCardChargebackStatementTest extends BaseTest {
    private static final DocType DOC_TYPE = DocType.IB_CARD_CHARGEBACK_STATEMENT;
    private static final String FILE = "image.png";
    private final RequestSpecification request = given().formParam("doc_type", DOC_TYPE.toString());
    private final ObjectNode docContent = DocContent.load(DOC_TYPE);

    static final Arguments[] CHARGEBACK_REASONS = {
            argumentSet("не согласен с суммой списания", 1),
            argumentSet("не совершал операцию", 2),
            argumentSet("сумма списана дважды", 3),
            argumentSet("возврат товара не зачислен", 4),
            argumentSet("товар не доставлен или услуга не оказана", 5),
            argumentSet("оплачено другим способом", 6),
            argumentSet("иное", 7)
    };

    @BeforeEach
    void setCardId() {
        DocContent.set(docContent, "cardId",
                find(success(given().formParam("doc_type", DOC_TYPE.toString()).post(PRIOR_INFO.path())), "accountList.cards.flatten().id[0]"));
    }

    @ParameterizedTest(name = "Заявление о несогласии с операцией по карте ({argumentSetName})")
    @FieldSource("CHARGEBACK_REASONS")
    void statement(int reasonType) {
        DocContent.set(docContent, "reasonType", reasonType);
        DocContent.attach(request, docContent, FILE);
        var prepared = request.formParam("doc_content", docContent.toString()).post(PREPARE_DOC.path());
        Allure.step("Проверки", () -> {
            matchesSchema(prepared, PREPARE_DOC.schema(DOC_TYPE));
            Allure.step("Карта совпадает с запросом", () -> assertThat(prepared.jsonPath().getLong("doc.cardId")).isEqualTo(docContent.get("cardId").asLong()));
            Allure.step("Причина несогласия совпадает с запросом", () -> assertThat(prepared.<Integer>path("doc.reasonType")).isEqualTo(reasonType));
            Allure.step("В заявлении одно вложение " + FILE, () -> assertThat(prepared.<List<String>>path("doc.attachmentsList.filename")).containsExactly(FILE));
            Allure.step("Список операций совпадает с запросом", () -> assertThat(prepared.<List<Number>>path("doc.opersList")).extracting(Number::longValue)
                    .containsExactlyElementsOf(docContent.withArray("opersList").valueStream().map(JsonNode::asLong).toList()));
        });
        SendDoc.send(DOC_TYPE, "SMSPWD", prepared);
    }

    @Nested
    @Negative
    class Negatives {
        static final Arguments[] REJECTIONS = {
                argumentSet("пустое описание", "description", "", "all", "Заполните description", new String[]{FILE}),
                argumentSet("без описания", "description", null, "all", "Заполните description", new String[]{FILE}),
                argumentSet("без карты", "cardId", null, "all", "Заполните cardId", new String[]{FILE}),
                argumentSet("пустой список операций", "opersList", List.of(), "all", "Укажите хотя бы одну операцию", new String[]{FILE}),
                argumentSet("без списка операций", "opersList", null, "all", "Укажите хотя бы одну операцию", new String[]{FILE}),
                argumentSet("без вложения", "attachmentsList", List.of(), "all", "Прикрепление документов обязательно", new String[0]),
                argumentSet("вложение без файла", "attachmentsList", List.of(Map.of("filename", FILE, "contentType", "image/png")),
                        "lobFile", "Ошибка обработки файла-вложения", new String[0])
        };

        @ParameterizedTest(name = "Заявление отклоняется проверками ({argumentSetName})")
        @FieldSource("REJECTIONS")
        void rejection(String field, Object value, String badItem, String errorMessage, String[] files) {
            DocContent.set(docContent, field, value);
            DocContent.attach(request, docContent, files);
            rejected(request.formParam("doc_content", docContent.toString()).post(PREPARE_DOC.path()), badItem, errorMessage);
        }

        @DisplayName("Заявление по несуществующей операции отклоняется")
        @Test
        void unknownOperation() {
            DocContent.set(docContent, "opersList", List.of(1));
            DocContent.attach(request, docContent, FILE);
            matchesSchema(request.formParam("doc_content", docContent.toString()).post(PREPARE_DOC.path()), "schemas/errors/check_failed.json");
        }

        @DisplayName("Подготовка без ticket отклоняется")
        @Test
        void withoutTicket() {
            DocContent.attach(request, docContent, FILE);
            error(request.auth().none().formParam("doc_content", docContent.toString()).post(PREPARE_DOC.path()), ErrorCode.AUTHORIZATION_REQUIRED);
        }
    }
}

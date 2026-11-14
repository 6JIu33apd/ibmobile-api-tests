package ru.avangard.qa.ibmobile.tests.doc;

import com.fasterxml.jackson.databind.JsonNode;
import io.restassured.specification.RequestSpecification;
import io.restassured.response.Response;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import ru.avangard.qa.ibmobile.api.DocType;
import ru.avangard.qa.ibmobile.core.*;
import ru.avangard.qa.ibmobile.tests.Section;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.MULTIPART;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static ru.avangard.qa.ibmobile.api.Endpoint.*;
import static ru.avangard.qa.ibmobile.api.ErrorCode.*;
import static ru.avangard.qa.ibmobile.api.ResponseChecks.*;

@ResourceLock("prepared_doc")
@Epic(Section.STATEMENTS)
@Feature("Заявление о несогласии с операцией по карте")
class IbCardChargebackStatementTest extends BaseTest {
    private static final DocType DOC_TYPE = DocType.IB_CARD_CHARGEBACK_STATEMENT;
    private static final String FILE = "image.png";

    static final Arguments[] CHARGEBACK_REASONS = {
        argumentSet("не согласен с суммой списания", 1),
        argumentSet("не совершал операцию", 2),
        argumentSet("сумма списана дважды", 3),
        argumentSet("возврат товара не зачислен", 4),
        argumentSet("товар не доставлен или услуга не оказана", 5),
        argumentSet("оплачено другим способом", 6),
        argumentSet("иное", 7)
    };

    @ParameterizedTest(name = "Заявление о несогласии с операцией по карте ({argumentSetName})")
    @FieldSource("CHARGEBACK_REASONS")
    void statement(int reasonType) {
        ObjectNode docContent = docContent().put("reasonType", reasonType);
        List<Long> opersList = docContent.withArray("opersList").valueStream().map(JsonNode::asLong).toList();
        RequestSpecification request = given().contentType(MULTIPART).formParam("ticket", session().ticket);
        DocContent.attach(request, docContent, FILE);
        Response prepared = request.formParam("doc_type", DOC_TYPE.toString()).formParam("doc_content", docContent.toString()).post(PREPARE_DOC.path());
        success(prepared);
        matchesSchema(prepared, PREPARE_DOC.schema(DOC_TYPE));
        assertThat(prepared.<Number>path("doc.cardId").longValue()).as("карта").isEqualTo(docContent.get("cardId").asLong());
        assertThat(prepared.<Integer>path("doc.reasonType")).as("причина").isEqualTo(reasonType);
        assertThat(prepared.<List<String>>path("doc.attachmentsList.filename")).as("вложения").containsExactly(FILE);
        assertThat(prepared.<List<Number>>path("doc.opersList")).as("операции").extracting(Number::longValue).containsExactlyElementsOf(opersList);
        success(Confirm.send("SMSPWD", session(), prepared));
    }

    @Nested @Negative
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
            ObjectNode docContent = DocContent.set(docContent(), field, value);
            RequestSpecification request = given().contentType(MULTIPART).formParam("ticket", session().ticket);
            DocContent.attach(request, docContent, files);
            rejected(request.formParam("doc_type", DOC_TYPE.toString()).formParam("doc_content", docContent.toString())
                    .post(PREPARE_DOC.path()), badItem, errorMessage);
        }

        @DisplayName("Заявление по несуществующей операции отклоняется")
        @Test
        void unknownOperation() {
            ObjectNode docContent = DocContent.set(docContent(), "opersList", List.of(1));
            RequestSpecification request = given().contentType(MULTIPART).formParam("ticket", session().ticket);
            DocContent.attach(request, docContent, FILE);
            assertThat(request.formParam("doc_type", DOC_TYPE.toString()).formParam("doc_content", docContent.toString())
                    .post(PREPARE_DOC.path()).<String>path("resultCode")).as("отказ проверок").isEqualTo("FAILED");
        }

        @DisplayName("Подготовка без ticket отклоняется")
        @Test
        void withoutTicket() {
            ObjectNode docContent = docContent();
            RequestSpecification request = given().contentType(MULTIPART);
            DocContent.attach(request, docContent, FILE);
            error(request.formParam("doc_type", DOC_TYPE.toString()).formParam("doc_content", docContent.toString()).post(PREPARE_DOC.path()), AUTHORIZATION_REQUIRED);
        }
    }

    private ObjectNode docContent() {
        context.put("priorInfo." + DOC_TYPE,
                success(given().formParam("ticket", session().ticket).formParam("doc_type", DOC_TYPE.toString()).post(PRIOR_INFO.path())));
        Number cardId = context.find("priorInfo." + DOC_TYPE, "accountList.cards.flatten().id[0]");
        return DocContent.load(DOC_TYPE).put("cardId", cardId.longValue()).put("description", "Заявление автотеста о несогласии с операцией");
    }
}

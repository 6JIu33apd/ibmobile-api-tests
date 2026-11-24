package ru.avangard.qa.ibmobile.tests.doc;

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
@Feature("Заявление в свободной форме")
@BaseTest.PreProd
class IbFreeFormatStatementTest extends BaseTest {
    private static final DocType DOC_TYPE = DocType.IB_FREE_FORMAT_STATEMENT;
    private final RequestSpecification request = given().formParam("doc_type", DOC_TYPE.toString());
    private final ObjectNode docContent = DocContent.load(DOC_TYPE);

    static final Arguments[] FILES = {
            argumentSet("без вложения", List.of()),
            argumentSet("с вложением", List.of("image.png"))
    };

    @ParameterizedTest(name = "Заявление в свободной форме {argumentSetName}")
    @FieldSource("FILES")
    void statement(List<String> files) {
        DocContent.attach(request, docContent, files.toArray(String[]::new));
        var prepared = request.formParam("doc_content", docContent.toString()).post(PREPARE_DOC.path());
        Allure.step("Проверки", () -> {
            matchesSchema(prepared, PREPARE_DOC.schema(DOC_TYPE));
            Allure.step("Текст заявления совпадает с запросом", () -> assertThat(prepared.<String>path("doc.text")).isEqualTo(docContent.get("text").asText()));
            Allure.step("Имена вложений совпадают с запросом", () -> assertThat(prepared.<List<String>>path("doc.attachmentsList.filename")).isEqualTo(files));
        });
    }

    @Nested @Negative
    class Negatives {
        static final Arguments[] REJECTIONS = {
                argumentSet("пустой текст", "text", "", "text", "Заполните текст заявления"),
                argumentSet("без текста", "text", null, "text", "Заполните текст"),
                argumentSet("вложение без файла", "attachmentsList", List.of(Map.of("filename", "image.png", "contentType", "image/png")),
                        "lobFile", "Ошибка обработки файла-вложения")
        };

        @ParameterizedTest(name = "Заявление отклоняется проверками ({argumentSetName})")
        @FieldSource("REJECTIONS")
        void rejection(String field, Object value, String badItem, String errorMessage) {
            DocContent.set(docContent, field, value);
            rejected(request.formParam("doc_content", docContent.toString()).post(PREPARE_DOC.path()), badItem, errorMessage);
        }
    }
}

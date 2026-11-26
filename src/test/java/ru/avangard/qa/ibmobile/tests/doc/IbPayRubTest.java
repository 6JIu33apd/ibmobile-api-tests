package ru.avangard.qa.ibmobile.tests.doc;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qameta.allure.*;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import ru.avangard.qa.ibmobile.api.DocType;
import ru.avangard.qa.ibmobile.core.*;
import ru.avangard.qa.ibmobile.tests.Section;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static ru.avangard.qa.ibmobile.api.AuthMethod.SMSPWD;
import static ru.avangard.qa.ibmobile.api.Endpoint.PREPARE_DOC;
import static ru.avangard.qa.ibmobile.api.Endpoint.SEND_DOC;
import static ru.avangard.qa.ibmobile.api.ResponseCheck.matchesSchema;

@ResourceLock("prepared_doc")
@Epic(Section.PAYMENTS)
@Feature("Внешний рублевый перевод")
class IbPayRubTest extends BaseTest {

    private static final DocType DOC_TYPE = DocType.IB_PAY_RUB;
    private final RequestSpecification request = given().formParam("doc_type", DOC_TYPE.toString());
    private final ObjectNode docContent = DocContent.load(DOC_TYPE);

    static Stream<Arguments> files() {
        return Stream.of(
                arguments("без вложения", List.of()),
                arguments("с вложением", List.of("image.png")));
    }

    static Stream<Arguments> transfers() {
        return Stream.of(
                argumentSet("себе в Т-Банк", "7710140679", "АО \"ТБанк\" г Москва", "044525974", "40817810800005857200"),
                argumentSet("другому в Альфу","7728168971", "АО \"Альфа-Банка\", г. Москва", "044525593", "40817810504981347375"));
    }

    @ParameterizedTest(name = "Внешний рублевый перевод {argumentSetName}")
    @MethodSource("transfers")
    void ibPayRub(String rCliInn, String rBankName, String rBankBic, String accCred) {
        var amountDeb = BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(101, 300), 2);
        docContent.put("amountDeb", amountDeb).put("accCred", accCred)
                .put("RCliInn", rCliInn).put("RBankName", rBankName).put("RBankBic", rBankBic);
        DocContent.attach(request, docContent, "image.png");
        var prepared = request.formParam("doc_content", docContent.toString()).post(PREPARE_DOC.path());
        Allure.step("Проверки", () -> {
            matchesSchema(prepared, PREPARE_DOC.schema(DOC_TYPE));
            Allure.step("Сумма списания", () -> assertThat(prepared.jsonPath().getObject("doc.amountDeb", BigDecimal.class)).isEqualByComparingTo(amountDeb));
            Allure.step("Счёт списания", () -> assertThat(prepared.<String>path("doc.accDeb")).isEqualTo(docContent.get("accDeb").asText()));
            Allure.step("Счёт зачисления", () -> assertThat(prepared.<String>path("doc.accCred")).isEqualTo(accCred));
        });
        var sent = Confirm.authMethod(SMSPWD, prepared).post(SEND_DOC.path());
        Allure.step("Проверки", () -> {
            matchesSchema(sent, SEND_DOC.schema(DOC_TYPE));
        });
    }

    @Nested
    @Negative
    class Negatives {
    }
}

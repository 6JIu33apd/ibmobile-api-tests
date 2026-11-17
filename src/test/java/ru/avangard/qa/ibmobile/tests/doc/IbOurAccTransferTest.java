package ru.avangard.qa.ibmobile.tests.doc;

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

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.MULTIPART;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static ru.avangard.qa.ibmobile.api.Endpoint.*;
import static ru.avangard.qa.ibmobile.api.ErrorCode.*;
import static ru.avangard.qa.ibmobile.api.ResponseChecks.*;

@ResourceLock("prepared_doc")
@Epic(Section.PAYMENTS)
@Feature("Перевод между своими счетами")
class IbOurAccTransferTest extends BaseTest {
    private static final DocType DOC_TYPE = DocType.IB_OUR_ACC_TRANSFER;
    private static final String DEBIT = "it.cardAcc && !it.isCredit && it.currency == 'RUR'";

    static final Arguments[] TRANSFERS = {
        argumentSet("с рублёвого дебетового счёта", DEBIT, "NONE"),
        argumentSet("с рублёвого кредитного счёта", "it.isCredit && it.currency == 'RUR'", "SMSPWD")
    };

    @ParameterizedTest(name = "Перевод между своими счетами {argumentSetName}")
    @FieldSource("TRANSFERS")
    void transfer(String accDebCondition, String authMethod) {
        BigDecimal amount = BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(101, 1000), 2);
        ObjectNode docContent = docContent(accDebCondition).put("amount", amount);
        Response prepared = given().contentType(MULTIPART)
                .formParam("doc_type", DOC_TYPE.toString()).formParam("doc_content", docContent.toString()).post(PREPARE_DOC.path());
        success(prepared);
        matchesSchema(prepared, PREPARE_DOC.schema(DOC_TYPE));
        assertThat(prepared.jsonPath().getObject("doc.amount", BigDecimal.class)).as("сумма").isEqualByComparingTo(amount);
        assertThat(prepared.<String>path("doc.accDeb")).as("счёт списания").isEqualTo(docContent.get("accDeb").asText());
        assertThat(prepared.<String>path("doc.accCred")).as("счёт зачисления").isEqualTo(docContent.get("accCred").asText());
        success(Confirm.send(authMethod, prepared));
    }

    @Nested @Negative
    class Negatives {
        static final Arguments[] INVALID_CASES = {
            argumentSet("нулевая сумма", "amount", 0),
            argumentSet("отрицательная сумма", "amount", new BigDecimal("-1.00")),
            argumentSet("без суммы", "amount", null)
        };

        @ParameterizedTest(name = "Перевод с некорректными параметрами отклоняется ({argumentSetName})")
        @FieldSource("INVALID_CASES")
        void invalidParameters(String field, Object value) {
            error(given().contentType(MULTIPART).formParam("doc_type", DOC_TYPE.toString())
                    .formParam("doc_content", DocContent.set(docContent(DEBIT), field, value).toString()).post(PREPARE_DOC.path()), INVALID_PARAMETERS);
        }

        static final Arguments[] REJECTIONS = {
            argumentSet("больше остатка", "amount", new BigDecimal("100000000.00"), "amountDeb", "Недостаточно средств на счете списания"),
            argumentSet("с чужого счёта", "accDeb", "40817810000000000001", "all", "Счет не найден"),
            argumentSet("на чужой счёт", "accCred", "40817810000000000001", "all", "Счет не найден")
        };

        @ParameterizedTest(name = "Перевод отклоняется проверками ({argumentSetName})")
        @FieldSource("REJECTIONS")
        void rejection(String field, Object value, String badItem, String errorMessage) {
            rejected(given().contentType(MULTIPART).formParam("doc_type", DOC_TYPE.toString())
                    .formParam("doc_content", DocContent.set(docContent(DEBIT), field, value).toString()).post(PREPARE_DOC.path()), badItem, errorMessage);
        }

        @DisplayName("Перевод на тот же счёт отклоняется")
        @Test
        void sameAccount() {
            ObjectNode docContent = docContent(DEBIT);
            DocContent.set(docContent, "accCred", docContent.get("accDeb").asText());
            rejected(given().contentType(MULTIPART)
                    .formParam("doc_type", DOC_TYPE.toString()).formParam("doc_content", docContent.toString()).post(PREPARE_DOC.path()),
                    "accCred", "Счет зачисления должен отличаться от счета списания");
        }

        @DisplayName("Подготовка без ticket отклоняется")
        @Test
        void withoutTicket() {
            error(given().auth().none().contentType(MULTIPART).formParam("doc_type", DOC_TYPE.toString())
                    .formParam("doc_content", docContent(DEBIT).toString()).post(PREPARE_DOC.path()), AUTHORIZATION_REQUIRED);
        }

    }

    private ObjectNode docContent(String accDebCondition) {
        var accList = success(given().post(ACC_LIST.path()));
        Map<String, Object> accDeb = find(accList, "accounts.account.findAll { " + accDebCondition + " }.max { it.otb }");
        String accCred = find(accList, "accounts.account.findAll { it.currency == '" + accDeb.get("currency")
                + "' && it.code != '" + accDeb.get("code") + "' }.max { it.otb }?.code");
        return DocContent.load(DOC_TYPE).put("accDeb", accDeb.get("code").toString()).put("accCred", accCred);
    }
}

package ru.avangard.qa.ibmobile.tests.doc;

import io.restassured.specification.RequestSpecification;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static ru.avangard.qa.ibmobile.api.Endpoint.*;
import static ru.avangard.qa.ibmobile.api.ErrorCode.*;
import static ru.avangard.qa.ibmobile.api.ResponseCheck.*;

@ResourceLock("prepared_doc")
@Epic(Section.PAYMENTS)
@Feature("Перевод между своими счетами")
class IbOurAccTransferTest extends BaseTest {
    private static final DocType DOC_TYPE = DocType.IB_OUR_ACC_TRANSFER;
    private static final String DEBIT = "it.cardAcc && !it.isCredit && it.currency == 'RUR'";
    private final RequestSpecification request = given().formParam("doc_type", DOC_TYPE.toString());
    private final ObjectNode docContent = DocContent.load(DOC_TYPE);

    static final Arguments[] TRANSFERS = {
        argumentSet("с рублёвого дебетового счёта", DEBIT, "NONE"),
        argumentSet("с рублёвого кредитного счёта", "it.isCredit && it.currency == 'RUR'", "SMSPWD")
    };

    @ParameterizedTest(name = "Перевод между своими счетами {argumentSetName}")
    @FieldSource("TRANSFERS")
    void transfer(String accDebCondition, String authMethod) {
        setAccounts(accDebCondition);
        var amount = BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(101, 1000), 2);
        docContent.put("amount", amount);
        var prepared = request.formParam("doc_content", docContent.toString()).post(PREPARE_DOC.path());
        Allure.step("Проверки", () -> {
            matchesSchema(prepared, PREPARE_DOC.schema(DOC_TYPE));
            Allure.step("Сумма перевода", () -> assertThat(prepared.jsonPath().getObject("doc.amount", BigDecimal.class)).isEqualByComparingTo(amount));
            Allure.step("Счёт списания", () -> assertThat(prepared.<String>path("doc.accDeb")).isEqualTo(docContent.get("accDeb").asText()));
            Allure.step("Счёт зачисления", () -> assertThat(prepared.<String>path("doc.accCred")).isEqualTo(docContent.get("accCred").asText()));
        });
        SendDoc.send(DOC_TYPE, authMethod, prepared);
    }

    @Nested @Negative
    class Negatives {
        @BeforeEach
        void setDebitAccounts() { setAccounts(DEBIT); }

        static final Arguments[] INVALID_AMOUNTS = {
            argumentSet("нулевая сумма", 0),
            argumentSet("отрицательная сумма", new BigDecimal("-1.00")),
            argumentSet("без суммы", (Object) null)
        };

        @ParameterizedTest(name = "Перевод с некорректными параметрами отклоняется ({argumentSetName})")
        @FieldSource("INVALID_AMOUNTS")
        void invalidParameters(Number amount) {
            DocContent.set(docContent, "amount", amount);
            error(request.formParam("doc_content", docContent.toString()).post(PREPARE_DOC.path()), INVALID_PARAMETERS);
        }

        static final Arguments[] REJECTIONS = {
            argumentSet("больше остатка", "amount", new BigDecimal("100000000.00"), "amountDeb", "Недостаточно средств на счете списания"),
            argumentSet("с чужого счёта", "accDeb", "40817810000000000001", "all", "Счет не найден"),
            argumentSet("на чужой счёт", "accCred", "40817810000000000001", "all", "Счет не найден")
        };

        @ParameterizedTest(name = "Перевод отклоняется проверками ({argumentSetName})")
        @FieldSource("REJECTIONS")
        void rejection(String field, Object value, String badItem, String errorMessage) {
            DocContent.set(docContent, field, value);
            rejected(request.formParam("doc_content", docContent.toString()).post(PREPARE_DOC.path()), badItem, errorMessage);
        }

        @DisplayName("Перевод на тот же счёт отклоняется")
        @Test
        void sameAccount() {
            DocContent.set(docContent, "accCred", docContent.get("accDeb").asText());
            rejected(request.formParam("doc_content", docContent.toString()).post(PREPARE_DOC.path()),
                    "accCred", "Счет зачисления должен отличаться от счета списания");
        }

        @DisplayName("Подготовка без ticket отклоняется")
        @Test
        void withoutTicket() {
            error(request.auth().none().formParam("doc_content", docContent.toString()).post(PREPARE_DOC.path()), AUTHORIZATION_REQUIRED);
        }
    }

    private void setAccounts(String accDebCondition) {
        var accList = success(given().post(ACC_LIST.path()));
        Map<String, Object> accDeb = find(accList, "accounts.account.findAll { " + accDebCondition + " }.max { it.otb }");
        DocContent.set(docContent, "accDeb", accDeb.get("code").toString());
        DocContent.set(docContent, "accCred", find(accList, "accounts.account.findAll { it.currency == '" + accDeb.get("currency")
                + "' && it.code != '" + accDeb.get("code") + "' }.max { it.otb }?.code"));
    }
}

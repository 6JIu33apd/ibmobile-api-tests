package ru.avangard.qa.ibmobile.tests.doc;

import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import ru.avangard.qa.ibmobile.api.DocType;
import ru.avangard.qa.ibmobile.core.*;
import ru.avangard.qa.ibmobile.tests.Section;

import java.util.*;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static ru.avangard.qa.ibmobile.api.Endpoint.*;
import static ru.avangard.qa.ibmobile.api.ErrorCode.*;
import static ru.avangard.qa.ibmobile.api.ResponseCheck.*;

@Epic(Section.REFS)
@Feature("Данные для документа")
class PriorInfoTest extends BaseTest {
    static final Arguments[] DOC_TYPES = {
        argumentSet("внешний рублёвый перевод", DocType.IB_PAY_RUB, "otherPersonDocumentList"),
        argumentSet("внутренний рублёвый платёж", DocType.IB_INSIDE_PAY_RUB, "otherPersonDocumentList"),
        argumentSet("заявление о несогласии с операцией", DocType.IB_CARD_CHARGEBACK_STATEMENT, "accountList.cards.flatten().id"),
        argumentSet("заявление на выпуск карты", DocType.IB_CARD_ORDER_STATEMENT, "availableAccounts")
    };

    @ParameterizedTest(name = "Данные для документа ({argumentSetName})")
    @FieldSource("DOC_TYPES")
    void priorInfo(DocType docType, String listPath) {
        var priorInfo = given().formParam("doc_type", docType.toString()).post(PRIOR_INFO.path());
        Allure.step("Проверки", () -> {
            matchesSchema(priorInfo, PRIOR_INFO.schema(docType));
            Allure.step("Список " + listPath + " заполнен", () -> assertThat(priorInfo.<List<?>>path(listPath)).isNotEmpty());
            if (docType == DocType.IB_CARD_CHARGEBACK_STATEMENT)
                Allure.step("Причины несогласия с операцией — от 1 до 7", () ->
                        assertThat(priorInfo.<List<Integer>>path("chargebackReasons.reasonType")).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7));
        });
    }

    @Nested @Negative
    class Negatives {
        static final Arguments[] INVALID_CASES = {
            argumentSet("без doc_type", Map.of()),
            argumentSet("неизвестный doc_type", Map.of("doc_type", "no_such_doc"))
        };

        @ParameterizedTest(name = "Запрос с некорректными параметрами отклоняется ({argumentSetName})")
        @FieldSource("INVALID_CASES")
        void invalidParameters(Map<String, String> params) {
            error(given().formParams(params).post(PRIOR_INFO.path()), INVALID_PARAMETERS);
        }

        @DisplayName("Запрос без ticket отклоняется")
        @Test
        void withoutTicket() {
            error(given().auth().none().formParam("doc_type", DocType.IB_PAY_RUB.toString()).post(PRIOR_INFO.path()), AUTHORIZATION_REQUIRED);
        }
    }
}

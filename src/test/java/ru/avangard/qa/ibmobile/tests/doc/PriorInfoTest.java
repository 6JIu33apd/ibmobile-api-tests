package ru.avangard.qa.ibmobile.tests.doc;

import io.restassured.response.Response;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import ru.avangard.qa.ibmobile.api.DocType;
import ru.avangard.qa.ibmobile.core.*;
import ru.avangard.qa.ibmobile.tests.Section;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static ru.avangard.qa.ibmobile.api.Endpoint.*;
import static ru.avangard.qa.ibmobile.api.ErrorCode.*;
import static ru.avangard.qa.ibmobile.api.ResponseChecks.*;

/** Контракт prior_info и наличие данных для документа; структура ответа и схема зависят от doc_type. */
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
        Response priorInfo = given().formParam("doc_type", docType.toString()).post(PRIOR_INFO.path());
        success(priorInfo);
        matchesSchema(priorInfo, PRIOR_INFO.schema(docType));
        assertThat(priorInfo.<List<?>>path(listPath)).as(listPath).isNotEmpty();
        if (docType == DocType.IB_CARD_CHARGEBACK_STATEMENT) {
            assertThat(priorInfo.<List<Integer>>path("chargebackReasons.reasonType")).as("причины несогласия")
                    .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7);
        }
    }

    @Nested @Negative
    class Negatives {
        static final Arguments[] INVALID_CASES = {
            argumentSet("без doc_type", (Object) null),
            argumentSet("неизвестный doc_type", "no_such_doc")
        };

        @ParameterizedTest(name = "Запрос с некорректными параметрами отклоняется ({argumentSetName})")
        @FieldSource("INVALID_CASES")
        void invalidParameters(Object value) {
            error(given().formParams(value == null ? Map.of() : Map.of("doc_type", value)).post(PRIOR_INFO.path()), INVALID_PARAMETERS);
        }

        @DisplayName("Запрос без ticket отклоняется")
        @Test
        void withoutTicket() {
            error(given().auth().none().formParam("doc_type", DocType.IB_PAY_RUB.toString()).post(PRIOR_INFO.path()), AUTHORIZATION_REQUIRED);
        }
    }
}

package ru.avangard.qa.ibmobile.tests.history;

import io.restassured.response.Response;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import ru.avangard.qa.ibmobile.core.BaseTest;
import ru.avangard.qa.ibmobile.tests.Section;

import java.util.*;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static ru.avangard.qa.ibmobile.api.Endpoint.FULL_HISTORY;
import static ru.avangard.qa.ibmobile.api.ErrorCode.*;
import static ru.avangard.qa.ibmobile.api.ResponseChecks.*;

@Epic(Section.HISTORY)
@Feature("Полная история операций")
class FullHistoryTest extends BaseTest {
    private static final Map<String, Object> PAGE = Map.of("method", "full", "offset", 0, "limit", 30);
    private static final Map<String, Object> DATES = Map.of("method", "full", "date_from", "01.01.2024", "date_to", "17.11.2026");
    private static final List<String> DOC_TYPES = List.of(
            "card_transaction", "card_auth_transaction", "ib_card2card", "ib_docs_mfer", "www_card_pay", "ib_card_pay",
            "ib_pay_rub", "ib_inside_pay_rub", "income_rub", "ib_inside_val", "ib_outside_val", "income_val",
            "ib_conv_fix_rate", "ib_our_acc_transfer", "cash_oper", "ib_c2c_fast_pay", "ib_c2c_fast_pay_receive",
            "ib_c2b_fps_pay", "ib_c2b_fps_pay_receive", "memo_order", "other_order");

    @DisplayName("История с обязательными параметрами")
    @Test
    void history() {
        Response response = given().formParams(PAGE).post(FULL_HISTORY.path());
        success(response);
        matchesSchema(response, FULL_HISTORY.schema());
        assertThat(response.<List<?>>path("history")).isNotEmpty();
    }

    @ParameterizedTest(name = "Все типы операций за период: направление {0}")
    @ValueSource(strings = {"1", "2"})
    void byDirection(String direction) {
        Response response = given().formParams(DATES).formParam("doc_type", DOC_TYPES)
                .formParam("debit_or_credit", direction).post(FULL_HISTORY.path());
        success(response);
        matchesSchema(response, FULL_HISTORY.schema());
        assertThat(response.<List<Map<String, Object>>>path("history")).isNotEmpty().allSatisfy(operation ->
                assertThat(operation).containsKey("srcType").containsEntry("debitOrCredit", direction));
    }

    @DisplayName("Несколько типов операций: количество соответствует limit")
    @Test
    void severalDocTypes() {
        List<String> docTypes = List.of("ib_pay_rub", "income_rub");
        Response response = given().formParams(PAGE).formParam("doc_type", docTypes).post(FULL_HISTORY.path());
        success(response);
        matchesSchema(response, FULL_HISTORY.schema());
        assertThat(response.<List<?>>path("history")).hasSize((Integer) PAGE.get("limit"));
        assertThat(response.<List<String>>path("history.srcType")).isSubsetOf(docTypes);
    }

    @Nested @Negative
    class Negatives {
        static final Arguments[] INVALID_CASES = {
            argumentSet("без периода и страницы", Map.of("method", "full")),
            argumentSet("без date_from", Map.of("method", "full", "date_to", DATES.get("date_to"))),
            argumentSet("без date_to", Map.of("method", "full", "date_from", DATES.get("date_from"))),
            argumentSet("без offset", Map.of("method", "full", "limit", PAGE.get("limit"))),
            argumentSet("без limit", Map.of("method", "full", "offset", PAGE.get("offset"))),
            argumentSet("даты в обратном порядке", Map.of("method", "full", "date_from", DATES.get("date_to"), "date_to", DATES.get("date_from")))
        };

        @ParameterizedTest(name = "Некорректная выборка истории отклоняется ({argumentSetName})")
        @FieldSource("INVALID_CASES")
        void invalidParameters(Map<String, Object> params) {
            error(given().formParams(params).post(FULL_HISTORY.path()), INVALID_PARAMETERS);
        }

        @DisplayName("Запрос без ticket отклоняется")
        @Test
        void withoutTicket() {
            error(given().auth().none().formParams(PAGE).post(FULL_HISTORY.path()), AUTHORIZATION_REQUIRED);
        }

        @DisplayName("Запрос с несуществующим ticket отклоняется")
        @Test
        void unknownTicket() {
            error(given().formParam("ticket", "invalid_ticket").formParams(PAGE).post(FULL_HISTORY.path()), AUTHORIZATION_REQUIRED);
        }
    }
}

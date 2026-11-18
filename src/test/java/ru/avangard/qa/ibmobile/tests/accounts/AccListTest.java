package ru.avangard.qa.ibmobile.tests.accounts;

import io.restassured.response.Response;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import ru.avangard.qa.ibmobile.core.*;
import ru.avangard.qa.ibmobile.tests.Section;

import java.util.*;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static ru.avangard.qa.ibmobile.api.Endpoint.*;
import static ru.avangard.qa.ibmobile.api.ErrorCode.*;
import static ru.avangard.qa.ibmobile.api.ResponseCheck.*;

@Epic(Section.ACCOUNTS)
@Feature("Список счетов")
class AccListTest extends BaseTest {
    @DisplayName("Счета клиента с уникальными 20-значными номерами")
    @Test
    void accList() {
        Response response = given().post(ACC_LIST.path());
        matchesSchema(response, ACC_LIST.schema());
        assertThat(response.<List<String>>path("accounts.account.code")).as("номера счетов").isNotEmpty().doesNotHaveDuplicates().allMatch(code -> code.matches("\\d{20}"));
    }

    @Nested @Negative
    class Negatives {
        static final Arguments[] INVALID_TICKETS = {
            argumentSet("Запрос без ticket отклоняется", Map.of()),
            argumentSet("Запрос с несуществующим ticket отклоняется", Map.of("ticket", "invalid_ticket"))
        };

        @ParameterizedTest(name = "{argumentSetName}")
        @FieldSource("INVALID_TICKETS")
        void invalidTicket(Map<String, String> params) {
            error(given().auth().none().formParams(params).post(ACC_LIST.path()), AUTHORIZATION_REQUIRED);
        }
    }
}

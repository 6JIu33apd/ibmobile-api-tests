package ru.avangard.qa.ibmobile.tests.accounts;

import io.restassured.response.Response;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import ru.avangard.qa.ibmobile.core.*;
import ru.avangard.qa.ibmobile.tests.Section;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static ru.avangard.qa.ibmobile.api.Endpoint.*;
import static ru.avangard.qa.ibmobile.api.ErrorCode.*;
import static ru.avangard.qa.ibmobile.api.ResponseChecks.*;

/** Контракт списка счетов, формат и уникальность номеров; отказы без действующего ticket. */
@Epic(Section.ACCOUNTS)
@Feature("Список счетов")
class AccListTest extends BaseTest {
    @DisplayName("Счета клиента с уникальными 20-значными номерами")
    @Test
    void accList() {
        Response response = given().formParam("ticket", session().ticket).post(ACC_LIST.path());
        success(response);
        matchesSchema(response, ACC_LIST.schema());
        assertThat(response.<List<String>>path("accounts.account.code")).as("номера счетов").isNotEmpty().doesNotHaveDuplicates().allMatch(code -> code.matches("\\d{20}"));
    }

    @Nested @Negative
    class Negatives {
        @DisplayName("Запрос без ticket отклоняется")
        @Test
        void withoutTicket() {
            error(given().post(ACC_LIST.path()), AUTHORIZATION_REQUIRED);
        }

        @DisplayName("Запрос с несуществующим ticket отклоняется")
        @Test
        void unknownTicket() {
            error(given().formParam("ticket", "invalid_ticket").post(ACC_LIST.path()), AUTHORIZATION_REQUIRED);
        }
    }
}

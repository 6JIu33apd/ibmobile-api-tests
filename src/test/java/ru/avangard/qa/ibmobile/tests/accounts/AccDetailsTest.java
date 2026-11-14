package ru.avangard.qa.ibmobile.tests.accounts;

import io.restassured.response.Response;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import ru.avangard.qa.ibmobile.core.*;
import ru.avangard.qa.ibmobile.tests.Section;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static ru.avangard.qa.ibmobile.api.Endpoint.*;
import static ru.avangard.qa.ibmobile.api.ErrorCode.*;
import static ru.avangard.qa.ibmobile.api.ResponseChecks.*;

@Epic(Section.ACCOUNTS)
@Feature("Деталка счета")
class AccDetailsTest extends BaseTest {
    private static final String DEBIT = "it.cardAcc && !it.isCredit && it.currency == 'RUR'";

    static final Arguments[] ACCOUNTS = {
        argumentSet("рублёвый дебетовый", DEBIT),
        argumentSet("валютный дебетовый", "it.cardAcc && !it.isCredit && it.currency != 'RUR'"),
        argumentSet("рублёвый кредитный", "it.isCredit && it.currency == 'RUR'"),
        argumentSet("валютный кредитный", "it.isCredit && it.currency != 'RUR'"),
        argumentSet("рублёвый текущий", "it.currAcc && it.currency == 'RUR'"),
        argumentSet("валютный текущий", "it.currAcc && it.currency != 'RUR'"),
        argumentSet("депозитный", "it.idepAcc")
    };

    @ParameterizedTest(name = "Деталка счёта из списка счетов ({argumentSetName})")
    @FieldSource("ACCOUNTS")
    void details(String condition) {
        context.put("accList", success(given().formParam("ticket", session().ticket).post(ACC_LIST.path())));
        String account = context.find("accList", "accounts.account.findAll { " + condition + " }.sort { -it.otb }[0]?.code");
        Response details = given().formParam("ticket", session().ticket).formParam("account", account).post(ACC_DETAILS.path());
        success(details);
        matchesSchema(details, ACC_DETAILS.schema());
        assertThat(details.<String>path("accBalanceDetails[0].account")).as("счёт").isEqualTo(account);
    }

    @Nested @Negative
    class Negatives {
        static final Arguments[] INVALID_CASES = {
            argumentSet("без счёта", (Object) null),
            argumentSet("некорректный номер счёта", "123")
        };

        @ParameterizedTest(name = "Запрос с некорректными параметрами отклоняется ({argumentSetName})")
        @FieldSource("INVALID_CASES")
        void invalidParameters(Object value) {
            error(given().formParam("ticket", session().ticket)
                    .formParams(value == null ? Map.of() : Map.of("account", value)).post(ACC_DETAILS.path()), INVALID_PARAMETERS);
        }

        @DisplayName("Запрос без ticket отклоняется")
        @Test
        void withoutTicket() {
            context.put("accList", success(given().formParam("ticket", session().ticket).post(ACC_LIST.path())));
            String account = context.find("accList", "accounts.account.findAll { " + DEBIT + " }.sort { -it.otb }[0]?.code");
            error(given().formParam("account", account).post(ACC_DETAILS.path()), AUTHORIZATION_REQUIRED);
        }
    }
}

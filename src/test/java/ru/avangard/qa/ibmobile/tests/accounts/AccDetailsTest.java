package ru.avangard.qa.ibmobile.tests.accounts;

import io.qameta.allure.*;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import ru.avangard.qa.ibmobile.core.BaseTest;
import ru.avangard.qa.ibmobile.tests.Section;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static ru.avangard.qa.ibmobile.api.Endpoint.*;
import static ru.avangard.qa.ibmobile.api.ErrorCode.*;
import static ru.avangard.qa.ibmobile.api.ResponseCheck.*;

@Epic(Section.ACCOUNTS)
@Feature("Деталка счета")
class AccDetailsTest extends BaseTest {
    private JsonPath accList;

    @BeforeEach
    void loadAccounts() { accList = success(given().post(ACC_LIST.path())); }

    static final Arguments[] ACCOUNTS = {
        argumentSet("рублёвый дебетовый", "it.cardAcc && !it.isCredit && it.currency == 'RUR'"),
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
        String account = find(accList, "accounts.account.findAll { " + condition + " }.max { it.otb }?.code");
        var accDetails = given().formParam("account", account).post(ACC_DETAILS.path());
        Allure.step("Проверки", () -> {
            matchesSchema(accDetails, ACC_DETAILS.schema());
            Allure.step("Номер счёта совпадает с запросом", () -> assertThat(accDetails.<String>path("accBalanceDetails[0].account")).isEqualTo(account));
        });
    }

    @Nested @Negative
    class Negatives {
        static final Arguments[] INVALID_ACCOUNTS = {
            argumentSet("без счёта", Map.of()),
            argumentSet("некорректный номер счёта", Map.of("account", "40810817000000002879"))
        };

        @ParameterizedTest(name = "Запрос с некорректными параметрами отклоняется ({argumentSetName})")
        @FieldSource("INVALID_ACCOUNTS")
        void invalidAccount(Map<String, String> params) {
            error(given().formParams(params).post(ACC_DETAILS.path()), INVALID_PARAMETERS);
        }

        @Test
        @DisplayName("Запрос без ticket отклоняется")
        void withoutTicket() {
            error(given().auth().none().formParam("account", (String) find(accList, "accounts.account[0].code")).post(ACC_DETAILS.path()), AUTHORIZATION_REQUIRED);
        }
    }
}

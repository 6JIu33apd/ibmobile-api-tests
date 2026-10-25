package ru.avangard.qa.ibmobile.tests.accounts;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.avangard.qa.ibmobile.core.BaseTest;
import ru.avangard.qa.ibmobile.core.Request;
import ru.avangard.qa.ibmobile.core.Section;
import ru.avangard.qa.ibmobile.data.Accounts;
import ru.avangard.qa.ibmobile.data.Accounts.Kind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static ru.avangard.qa.ibmobile.core.Endpoint.ACC_DETAILS;
import static ru.avangard.qa.ibmobile.core.ErrorCode.AUTHORIZATION_REQUIRED;
import static ru.avangard.qa.ibmobile.core.ErrorCode.INVALID_PARAMETERS;

@Epic(Section.ACCOUNTS)
@Feature("Деталка счета")
class AccDetailsTest extends BaseTest {

    @DisplayName("Деталка счёта из списка счетов")
    @ParameterizedTest(name = "({0})")
    @EnumSource(Kind.class)
    void details(Kind kind) {
        var accounts = Accounts.of(session(), kind);
        assumeFalse(accounts.isEmpty(), "Нет счёта: " + kind);
        String account = accounts.getFirst();

        var details = Request.to(ACC_DETAILS).as(session()).param("account", account).post().success().matchesSchema();
        assertThat(details.<String>path("accBalanceDetails[0].account")).as("счёт").isEqualTo(account);
    }

    @Story("Негативные")
    @DisplayName("Запрос без счёта отклоняется")
    @Test
    void withoutAccount() {
        Request.to(ACC_DETAILS).as(session()).post().error(INVALID_PARAMETERS);
    }

    @Story("Негативные")
    @DisplayName("Запрос с некорректным номером счёта отклоняется")
    @Test
    void invalidAccount() {
        Request.to(ACC_DETAILS).as(session()).param("account", "123").post().error(INVALID_PARAMETERS);
    }

    @Story("Негативные")
    @DisplayName("Запрос без ticket отклоняется")
    @Test
    void withoutTicket() {
        Request.to(ACC_DETAILS).param("account", "40817810000000000001").post().error(AUTHORIZATION_REQUIRED);
    }
}

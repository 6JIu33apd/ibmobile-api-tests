package ru.avangard.qa.ibmobile.tests.accounts;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.avangard.qa.ibmobile.api.Request;
import ru.avangard.qa.ibmobile.core.BaseTest;
import ru.avangard.qa.ibmobile.data.AccListData;
import ru.avangard.qa.ibmobile.data.AccListData.AccountType;
import ru.avangard.qa.ibmobile.tests.Section;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static ru.avangard.qa.ibmobile.api.Endpoint.ACC_DETAILS;
import static ru.avangard.qa.ibmobile.api.ErrorCode.AUTHORIZATION_REQUIRED;
import static ru.avangard.qa.ibmobile.api.ErrorCode.INVALID_PARAMETERS;

@Epic(Section.ACCOUNTS)
@Feature("Деталка счета")
class AccDetailsTest extends BaseTest {

    @DisplayName("Деталка счёта из списка счетов")
    @ParameterizedTest(name = "({0})")
    @EnumSource(AccountType.class)
    void details(AccountType type) {
        var accounts = AccListData.accounts(session(), type);
        assumeFalse(accounts.isEmpty(), "Нет счёта: " + type);
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

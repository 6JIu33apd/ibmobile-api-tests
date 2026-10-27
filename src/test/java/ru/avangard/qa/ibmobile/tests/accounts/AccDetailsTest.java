package ru.avangard.qa.ibmobile.tests.accounts;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import ru.avangard.qa.ibmobile.api.Request;
import ru.avangard.qa.ibmobile.core.BaseTest;
import ru.avangard.qa.ibmobile.data.AccListData;
import ru.avangard.qa.ibmobile.data.AccListData.AccountType;
import ru.avangard.qa.ibmobile.tests.Section;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static ru.avangard.qa.ibmobile.api.Endpoint.ACC_DETAILS;
import static ru.avangard.qa.ibmobile.api.ErrorCode.AUTHORIZATION_REQUIRED;
import static ru.avangard.qa.ibmobile.api.ErrorCode.INVALID_PARAMETERS;

/** Чужой валидный счёт даёт errorCode 2 internal_server_error — на разборе, кейса нет. */
@Epic(Section.ACCOUNTS)
@Feature("Деталка счета")
class AccDetailsTest extends BaseTest {

    @DisplayName("Деталка счёта из списка счетов")
    @ParameterizedTest(name = "({0})")
    @EnumSource(AccountType.class)
    void details(AccountType type) {
        String account = account(type);
        var details = Request.to(ACC_DETAILS).as(session()).param("account", account).post().success().matchesSchema();
        assertThat(details.<String>path("accBalanceDetails[0].account")).as("счёт").isEqualTo(account);
    }

    /** Параметр запроса по умолчанию → значение (null — без параметра). */
    static Stream<Arguments> invalidParameters() {
        return Stream.of(
                arguments("без счёта", "account", null),
                arguments("некорректный номер счёта", "account", "123"));
    }

    @Story("Негативные")
    @DisplayName("Запрос с некорректными параметрами отклоняется")
    @ParameterizedTest(name = "({0})")
    @MethodSource("invalidParameters")
    void invalidParameters(String title, String param, Object value) {
        Request.to(ACC_DETAILS).as(session()).param("account", account(AccountType.DEBIT)).param(param, value)
                .post().error(INVALID_PARAMETERS);
    }

    @Story("Негативные")
    @DisplayName("Запрос без ticket отклоняется")
    @Test
    void withoutTicket() {
        Request.to(ACC_DETAILS).param("account", account(AccountType.DEBIT)).post().error(AUTHORIZATION_REQUIRED);
    }

    private String account(AccountType type) {
        return new AccListData(session()).first(type);
    }
}

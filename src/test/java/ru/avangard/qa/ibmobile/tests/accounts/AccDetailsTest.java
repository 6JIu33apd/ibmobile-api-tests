package ru.avangard.qa.ibmobile.tests.accounts;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.avangard.qa.ibmobile.api.Request;
import ru.avangard.qa.ibmobile.core.BaseTest;
import ru.avangard.qa.ibmobile.core.Negative;
import ru.avangard.qa.ibmobile.data.AccListData;
import ru.avangard.qa.ibmobile.tests.Section;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static ru.avangard.qa.ibmobile.api.Endpoint.ACC_DETAILS;
import static ru.avangard.qa.ibmobile.api.ErrorCode.AUTHORIZATION_REQUIRED;
import static ru.avangard.qa.ibmobile.api.ErrorCode.INVALID_PARAMETERS;

/** Чужой валидный счёт даёт errorCode 2 internal_server_error — на разборе, кейса нет. */
@Epic(Section.ACCOUNTS)
@Feature("Деталка счета")
class AccDetailsTest extends BaseTest {

    private static final String DEBIT = "it.cardAcc && !it.isCredit && it.currency == 'RUR'";

    /** Счёт — условие по полям acc_list. */
    static Stream<Arguments> accounts() {
        return Stream.of(
                argumentSet("рублёвый дебетовый", DEBIT),
                argumentSet("валютный дебетовый", "it.cardAcc && !it.isCredit && it.currency != 'RUR'"),
                argumentSet("рублёвый кредитный", "it.isCredit && it.currency == 'RUR'"),
                argumentSet("валютный кредитный", "it.isCredit && it.currency != 'RUR'"),
                argumentSet("рублёвый текущий", "it.currAcc && it.currency == 'RUR'"),
                argumentSet("валютный текущий", "it.currAcc && it.currency != 'RUR'"),
                argumentSet("депозитный", "it.idepAcc"));
    }

    @DisplayName("Деталка счёта из списка счетов")
    @ParameterizedTest(name = "({argumentSetName})")
    @MethodSource("accounts")
    void details(String condition) {
        String account = account(condition);
        var details = Request.to(ACC_DETAILS).as(session()).param("account", account).post().success().matchesSchema();
        assertThat(details.<String>path("accBalanceDetails[0].account")).as("счёт").isEqualTo(account);
    }

    @Nested
    @Negative
    class Negatives {

        /** Параметр запроса по умолчанию → значение (null — без параметра). */
        static Stream<Arguments> invalidParameters() {
            return Stream.of(
                    argumentSet("без счёта", "account", null),
                    argumentSet("некорректный номер счёта", "account", "123"));
        }

        @DisplayName("Запрос с некорректными параметрами отклоняется")
        @ParameterizedTest(name = "({argumentSetName})")
        @MethodSource("invalidParameters")
        void invalidParameters(String param, Object value) {
            Request.to(ACC_DETAILS).as(session()).param("account", account(DEBIT)).param(param, value)
                    .post().error(INVALID_PARAMETERS);
        }

        @DisplayName("Запрос без ticket отклоняется")
        @Test
        void withoutTicket() {
            Request.to(ACC_DETAILS).param("account", account(DEBIT)).post().error(AUTHORIZATION_REQUIRED);
        }
    }

    private String account(String condition) {
        return new AccListData(session()).codes(condition).getFirst();
    }
}

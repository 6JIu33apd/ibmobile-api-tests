package ru.avangard.qa.ibmobile.tests.accounts;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.avangard.qa.ibmobile.core.Request;
import ru.avangard.qa.ibmobile.core.Session;
import ru.avangard.qa.ibmobile.core.User;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static ru.avangard.qa.ibmobile.api.Endpoint.ACC_LIST;

@Epic("Счета")
@Feature("Statement/acc_list")
class AccListTest {

    static Stream<Arguments> cases() {
        return Stream.of(
                arguments("Клиент с карточными и валютными счетами", User.MAIN));
    }

    @DisplayName("Список счетов")
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void accList(String title, User user) {
        try (var s = Session.login(user)) {
            var response = Request.to(ACC_LIST).as(s).post().success().matchesSchema();

            List<String> codes = response.path("accounts.account.code");
            assertThat(codes).as("номера счетов")
                    .isNotEmpty()
                    .doesNotHaveDuplicates()
                    .allMatch(code -> code.matches("\\d{20}"));
        }
    }
}

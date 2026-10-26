package ru.avangard.qa.ibmobile.tests.accounts;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.avangard.qa.ibmobile.api.Request;
import ru.avangard.qa.ibmobile.config.Profile;
import ru.avangard.qa.ibmobile.core.BaseTest;
import ru.avangard.qa.ibmobile.core.Login;
import ru.avangard.qa.ibmobile.tests.Section;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.avangard.qa.ibmobile.api.Endpoint.ACC_LIST;
import static ru.avangard.qa.ibmobile.api.ErrorCode.AUTHORIZATION_REQUIRED;

@Epic(Section.ACCOUNTS)
@Feature("Список счетов")
class AccListTest extends BaseTest {

    @DisplayName("Счета клиента с уникальными 20-значными номерами")
    @Test
    @Login(Profile.KILLER)
    void accList() {
        List<String> codes = Request.to(ACC_LIST).as(session()).post().success().matchesSchema().path("accounts.account.code");
        assertThat(codes).as("номера счетов").isNotEmpty().doesNotHaveDuplicates().allMatch(code -> code.matches("\\d{20}"));
    }

    @Story("Негативные")
    @DisplayName("Запрос без ticket отклоняется")
    @Test
    void withoutTicket() {
        Request.to(ACC_LIST).post().error(AUTHORIZATION_REQUIRED);
    }
}

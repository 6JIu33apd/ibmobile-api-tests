package ru.avangard.qa.ibmobile.tests.accounts;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.avangard.qa.ibmobile.core.BaseTest;
import ru.avangard.qa.ibmobile.core.Request;
import ru.avangard.qa.ibmobile.core.Section;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.avangard.qa.ibmobile.core.Endpoint.ACC_LIST;
import static ru.avangard.qa.ibmobile.core.ErrorCode.AUTHORIZATION_REQUIRED;

@Epic(Section.ACCOUNTS)
@Feature("Список счетов")
class AccListTest extends BaseTest {

    @DisplayName("Счета клиента с уникальными 20-значными номерами")
    @Test
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

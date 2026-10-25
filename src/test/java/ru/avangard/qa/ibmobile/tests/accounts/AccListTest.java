package ru.avangard.qa.ibmobile.tests.accounts;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.avangard.qa.ibmobile.core.BaseTest;
import ru.avangard.qa.ibmobile.core.Login;
import ru.avangard.qa.ibmobile.core.Request;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.avangard.qa.ibmobile.core.Endpoint.ACC_LIST;
import static ru.avangard.qa.ibmobile.core.Profile.MAIN;

@Epic("Счета")
@Feature("Statement/acc_list")
@Login(MAIN)
class AccListTest extends BaseTest {

    @DisplayName("Список счетов")
    @Test
    void accList() {
        List<String> codes = Request.to(ACC_LIST).as(session).post().success().matchesSchema().path("accounts.account.code");
        assertThat(codes).as("номера счетов").isNotEmpty().doesNotHaveDuplicates().allMatch(code -> code.matches("\\d{20}"));
    }
}

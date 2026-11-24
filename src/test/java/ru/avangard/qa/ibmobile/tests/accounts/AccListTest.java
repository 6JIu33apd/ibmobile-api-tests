package ru.avangard.qa.ibmobile.tests.accounts;

import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import ru.avangard.qa.ibmobile.core.*;
import ru.avangard.qa.ibmobile.tests.Section;

import java.util.*;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static ru.avangard.qa.ibmobile.api.Endpoint.*;
import static ru.avangard.qa.ibmobile.api.ErrorCode.*;
import static ru.avangard.qa.ibmobile.api.ResponseCheck.*;

@Epic(Section.ACCOUNTS)
@Feature("Список счетов")
class AccListTest extends BaseTest {
    @DisplayName("Список счетов")
    @Test
    void accList() {
        var accList = given().post(ACC_LIST.path());
        Allure.step("Проверки", () -> {
            matchesSchema(accList, ACC_LIST.schema());
            Allure.step("Список содержит уникальные номера счетов", () -> assertThat(accList.<List<String>>path("accounts.account.code"))
                    .isNotEmpty().doesNotHaveDuplicates().allMatch(code -> code.matches("\\d{20}")));
        });
    }

    @Nested
    @Negative
    class Negatives {
    }
}

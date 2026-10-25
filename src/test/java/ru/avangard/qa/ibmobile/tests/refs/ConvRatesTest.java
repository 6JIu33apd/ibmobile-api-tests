package ru.avangard.qa.ibmobile.tests.refs;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.avangard.qa.ibmobile.core.Request;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.avangard.qa.ibmobile.core.Endpoint.CONV_RATES;

@Epic("Справочники")
@Feature("Refs/conv_rates")
class ConvRatesTest {

    @DisplayName("Курсы конвертации без авторизации")
    @Test
    void convRates() {
        var response = Request.to(CONV_RATES).post().success().matchesSchema();

        List<Map<String, Object>> rates = response.path("rates");
        assertThat(rates).as("курсы").isNotEmpty().allSatisfy(rate ->
                assertThat(((Number) rate.get("buyRate")).doubleValue())
                        .as("покупка < продажи для %s/%s", rate.get("currencyFrom"), rate.get("currencyTo"))
                        .isLessThan(((Number) rate.get("sellRate")).doubleValue()));
    }
}

package ru.avangard.qa.ibmobile.data;

import ru.avangard.qa.ibmobile.api.ApiResponse;
import ru.avangard.qa.ibmobile.api.Request;
import ru.avangard.qa.ibmobile.api.Session;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static ru.avangard.qa.ibmobile.api.Endpoint.ACC_LIST;

/**
 * Ответ acc_list целиком — данные для других ручек. Какой счёт нужен, решает тест: условие GPath по полям счёта
 * ({@code it.cardAcc && !it.isCredit && it.currency == 'RUR'}). Любое другое поле — {@link #path}.
 */
public final class AccListData {

    private static final Pattern FIELD = Pattern.compile("it\\.(\\w+)");

    private final ApiResponse response;

    public AccListData(Session session) {
        response = Request.to(ACC_LIST).as(session).post().success();
    }

    /** Любое поле ответа, например path("accounts.account.code"). */
    public <T> T path(String path) {
        return response.path(path);
    }

    /**
     * Номера счетов по условию, от большей доступной суммы (otb) к меньшей: со счёта в начале есть что списать.
     * Нет таких счетов — кейс пропускается; поле, которого нет у счетов (опечатка), роняет тест.
     */
    public List<String> codes(String condition) {
        List<Map<String, Object>> accounts = path("accounts.account");
        var fields = accounts.stream().flatMap(account -> account.keySet().stream()).collect(Collectors.toSet());
        var unknown = FIELD.matcher(condition).results().map(field -> field.group(1)).filter(field -> !fields.contains(field)).toList();
        assertThat(unknown).as("нет у счетов полей из условия %s", condition).isEmpty();
        List<String> codes = path("accounts.account.findAll { " + condition + " }.sort { -it.otb }.code");
        assumeFalse(codes.isEmpty(), "Нет счёта: " + condition);
        return codes;
    }

    /** Поле счёта по номеру, например field(code, "currency"). */
    public <T> T field(String code, String field) {
        return path("accounts.account.find { it.code == '" + code + "' }." + field);
    }
}

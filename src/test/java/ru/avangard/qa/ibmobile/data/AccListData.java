package ru.avangard.qa.ibmobile.data;

import ru.avangard.qa.ibmobile.api.ApiResponse;
import ru.avangard.qa.ibmobile.api.Request;
import ru.avangard.qa.ibmobile.api.Session;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static ru.avangard.qa.ibmobile.api.Endpoint.ACC_LIST;
import static ru.avangard.qa.ibmobile.data.AccListData.AccountType.*;

/**
 * Ответ acc_list целиком — данные для других ручек. Любое поле — {@link #path}, частые выборки — методами ниже.
 * Берётся в тесте из его сессии: {@code new AccListData(session())} — у каждого пользователя свои данные.
 */
public final class AccListData {

    /**
     * Дебетовые и кредитные счета (cardAcc) могут иметь карты; карта — лишь прокси, списание всегда со счёта.
     * Текущие (currAcc) — только дебетовые, без карт. Депозитные (idepAcc) — дебетовые, без карт, со своей логикой.
     */
    public enum AccountType {
        DEBIT("рублёвый дебетовый"),
        DEBIT_FX("валютный дебетовый"),
        CREDIT("рублёвый кредитный"),
        CREDIT_FX("валютный кредитный"),
        CURRENT("рублёвый текущий"),
        CURRENT_FX("валютный текущий"),
        DEPOSIT("депозитный");

        private final String title;

        AccountType(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    /** От большей доступной суммы к меньшей: со счёта в начале списка есть что списать. */
    private static final Comparator<Map<String, Object>> BY_OTB =
            Comparator.comparing((Map<String, Object> account) -> new BigDecimal(String.valueOf(account.get("otb")))).reversed();

    private final ApiResponse response;

    public AccListData(Session session) {
        response = Request.to(ACC_LIST).as(session).post().success();
    }

    /** Любое поле ответа, например path("accounts.account.code"). */
    public <T> T path(String path) {
        return response.path(path);
    }

    /** Номера счетов типа type. */
    public List<String> accounts(AccountType type) {
        return codes(account -> typeOf(account) == type);
    }

    /** Первый счёт типа type; нет счёта — кейс пропускается. */
    public String first(AccountType type) {
        var accounts = accounts(type);
        assumeFalse(accounts.isEmpty(), "Нет счёта: " + type);
        return accounts.getFirst();
    }

    /** Номера других счетов в валюте счёта account. */
    public List<String> sameCurrency(String account) {
        Object currency = account(account).get("currency");
        return codes(other -> currency.equals(other.get("currency")) && !account.equals(other.get("code")));
    }

    private List<String> codes(Predicate<Map<String, Object>> filter) {
        List<Map<String, Object>> accounts = path("accounts.account");
        return accounts.stream().filter(filter).sorted(BY_OTB).map(account -> (String) account.get("code")).toList();
    }

    /** Пример выборки по GPath прямо из ответа. */
    private Map<String, Object> account(String code) {
        return path("accounts.account.find { it.code == '" + code + "' }");
    }

    /** null — счёт другого типа. */
    private static AccountType typeOf(Map<String, Object> account) {
        boolean rub = "RUR".equals(account.get("currency"));
        if (is(account, "cardAcc")) {
            return is(account, "credit") ? (rub ? CREDIT : CREDIT_FX) : (rub ? DEBIT : DEBIT_FX);
        }
        if (is(account, "currAcc")) {
            return rub ? CURRENT : CURRENT_FX;
        }
        return is(account, "idepAcc") ? DEPOSIT : null;
    }

    private static boolean is(Map<String, Object> account, String flag) {
        return Boolean.TRUE.equals(account.get(flag));
    }
}

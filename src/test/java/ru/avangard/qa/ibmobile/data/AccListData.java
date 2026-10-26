package ru.avangard.qa.ibmobile.data;

import ru.avangard.qa.ibmobile.api.Request;
import ru.avangard.qa.ibmobile.api.Session;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static ru.avangard.qa.ibmobile.api.Endpoint.ACC_LIST;
import static ru.avangard.qa.ibmobile.data.AccListData.AccountType.*;

/** Данные из acc_list для других ручек. */
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

    private AccListData() {
    }

    /** Номера счетов типа type, от большей доступной суммы (otb) к меньшей. */
    public static List<String> accounts(Session s, AccountType type) {
        List<Map<String, Object>> accounts = Request.to(ACC_LIST).as(s).post().success().path("accounts.account");
        return accounts.stream()
                .filter(a -> typeOf(a) == type)
                .sorted(Comparator.comparingDouble(a -> -((Number) a.get("otb")).doubleValue()))
                .map(a -> (String) a.get("code"))
                .toList();
    }

    /** null — счёт другого типа. */
    private static AccountType typeOf(Map<String, Object> a) {
        boolean rub = "RUR".equals(a.get("currency"));
        if (is(a, "cardAcc")) {
            return is(a, "credit") ? (rub ? CREDIT : CREDIT_FX) : (rub ? DEBIT : DEBIT_FX);
        }
        if (is(a, "currAcc")) {
            return rub ? CURRENT : CURRENT_FX;
        }
        return is(a, "idepAcc") ? DEPOSIT : null;
    }

    private static boolean is(Map<String, Object> account, String flag) {
        return Boolean.TRUE.equals(account.get(flag));
    }
}

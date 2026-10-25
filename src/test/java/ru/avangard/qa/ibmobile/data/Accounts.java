package ru.avangard.qa.ibmobile.data;

import ru.avangard.qa.ibmobile.core.Request;
import ru.avangard.qa.ibmobile.core.Session;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static ru.avangard.qa.ibmobile.core.Endpoint.ACC_LIST;

/**
 * Счета из acc_list — тестовые данные для других ручек.
 * Дебетовые и кредитные счета (cardAcc) могут иметь карты; карта — лишь прокси, списание всегда со счёта.
 * Текущие (currAcc) — только дебетовые, без карт. Депозитные (idepAcc) — дебетовые, без карт, со своей логикой.
 */
public final class Accounts {

    public enum Kind {
        DEBIT("рублёвый дебетовый", a -> is(a, "cardAcc") && !is(a, "credit") && rub(a)),
        DEBIT_FX("валютный дебетовый", a -> is(a, "cardAcc") && !is(a, "credit") && !rub(a)),
        CREDIT("рублёвый кредитный", a -> is(a, "cardAcc") && is(a, "credit") && rub(a)),
        CREDIT_FX("валютный кредитный", a -> is(a, "cardAcc") && is(a, "credit") && !rub(a)),
        CURRENT("рублёвый текущий", a -> is(a, "currAcc") && rub(a)),
        CURRENT_FX("валютный текущий", a -> is(a, "currAcc") && !rub(a)),
        DEPOSIT("депозитный", a -> is(a, "idepAcc"));

        private final String title;
        private final Predicate<Map<String, Object>> filter;

        Kind(String title, Predicate<Map<String, Object>> filter) {
            this.title = title;
            this.filter = filter;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private Accounts() {
    }

    /** Номера счетов вида kind, от большей доступной суммы (otb) к меньшей. */
    public static List<String> of(Session s, Kind kind) {
        List<Map<String, Object>> accounts = Request.to(ACC_LIST).as(s).post().success().path("accounts.account");
        return accounts.stream()
                .filter(kind.filter)
                .sorted(Comparator.comparingDouble(a -> -((Number) a.get("otb")).doubleValue()))
                .map(a -> (String) a.get("code"))
                .toList();
    }

    private static boolean is(Map<String, Object> account, String flag) {
        return Boolean.TRUE.equals(account.get(flag));
    }

    private static boolean rub(Map<String, Object> account) {
        return "RUR".equals(account.get("currency"));
    }
}

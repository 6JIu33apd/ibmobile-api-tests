package ru.avangard.qa.ibmobile.data;

import ru.avangard.qa.ibmobile.core.Request;
import ru.avangard.qa.ibmobile.core.Session;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static ru.avangard.qa.ibmobile.api.Endpoint.ACC_LIST;

/** Счета пользователя из acc_list — тестовые данные для других ручек. */
public final class Accounts {

    private Accounts() {
    }

    /** Все счета: элементы accounts[].account из acc_list. */
    public static List<Map<String, Object>> all(Session session) {
        return Request.to(ACC_LIST).as(session).post().success().path("accounts.account");
    }

    /**
     * Рублёвые карточные счета без кредитного лимита, от большего доступного остатка (otb) к меньшему.
     * Списание с собственных средств не требует подтверждения.
     */
    public static List<String> rubDebitCardAccounts(Session session) {
        return rubCardAccounts(session, false);
    }

    /** Рублёвые кредитные карточные счета. Списание с них требует подтверждения (SMS+пароль, код с карты, QR). */
    public static List<String> rubCreditCardAccounts(Session session) {
        return rubCardAccounts(session, true);
    }

    private static List<String> rubCardAccounts(Session session, boolean credit) {
        return all(session).stream()
                .filter(a -> "RUR".equals(a.get("currency"))
                        && Boolean.TRUE.equals(a.get("cardAcc"))
                        && credit == Boolean.TRUE.equals(a.get("credit")))
                .sorted(Comparator.comparingDouble((Map<String, Object> a) -> ((Number) a.get("otb")).doubleValue()).reversed())
                .map(a -> (String) a.get("code"))
                .toList();
    }
}

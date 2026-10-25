package ru.avangard.qa.ibmobile.data;

import ru.avangard.qa.ibmobile.core.Request;
import ru.avangard.qa.ibmobile.core.Session;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static ru.avangard.qa.ibmobile.core.Endpoint.ACC_LIST;

/** Счета из acc_list — тестовые данные для других ручек. */
public final class Accounts {

    private Accounts() {
    }

    /** Рублёвые карточные счета, кредитные или нет, от большего доступного остатка к меньшему. */
    public static List<String> rubCard(Session s, boolean credit) {
        List<Map<String, Object>> accounts = Request.to(ACC_LIST).as(s).post().success().path("accounts.account");
        return accounts.stream()
                .filter(a -> "RUR".equals(a.get("currency")) && (boolean) a.get("cardAcc") && credit == (boolean) a.get("credit"))
                .sorted(Comparator.comparingDouble(a -> -((Number) a.get("otb")).doubleValue()))
                .map(a -> (String) a.get("code"))
                .toList();
    }
}

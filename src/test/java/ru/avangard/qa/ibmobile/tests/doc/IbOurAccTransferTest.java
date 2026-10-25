package ru.avangard.qa.ibmobile.tests.doc;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.avangard.qa.ibmobile.core.BaseTest;
import ru.avangard.qa.ibmobile.core.Doc;
import ru.avangard.qa.ibmobile.core.Doc.Confirm;
import ru.avangard.qa.ibmobile.core.Login;
import ru.avangard.qa.ibmobile.core.Template;
import ru.avangard.qa.ibmobile.data.Accounts;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static ru.avangard.qa.ibmobile.core.Profile.MAIN;

@Epic("Переводы")
@Feature("ib_our_acc_transfer")
@Tag("mutating")
@Login(MAIN)
@Execution(ExecutionMode.SAME_THREAD) // документ живёт в сессии; похожий перевод, пока обрабатывается предыдущий, — дубль
class IbOurAccTransferTest extends BaseTest {

    private static final String DOC = "ib_our_acc_transfer";

    static Stream<Arguments> cases() {
        return Stream.of(
                arguments("С собственных средств", false, Confirm.NONE),
                arguments("С кредитного счёта, SMS + пароль", true, Confirm.SMSPWD),
                arguments("С кредитного счёта, скретч-карта", true, Confirm.SCRETCH));
    }

    @DisplayName("Перевод между своими счетами")
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void transfer(String title, boolean fromCredit, Confirm confirm) {
        List<String> from = Accounts.rubCard(session, fromCredit);
        List<String> to = Accounts.rubCard(session, false);
        assumeTrue(!from.isEmpty() && to.size() >= 2, "Не хватает рублёвых карточных счетов");

        var amount = BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(101, 1000), 2); // та же сумма — дубль
        var content = Template.of(DOC).with("amount", amount).with("accDeb", from.get(0)).with("accCred", to.get(1));

        var prepared = Doc.prepare(session, DOC, content).success().matchesSchema(DOC);
        var sent = Doc.send(session, prepared, confirm).success().matchesSchema(DOC);
        assertThat(new BigDecimal(sent.path("doc.amount").toString())).as("сумма").isEqualByComparingTo(amount);
    }
}

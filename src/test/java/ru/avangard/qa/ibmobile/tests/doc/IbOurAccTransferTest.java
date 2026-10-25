package ru.avangard.qa.ibmobile.tests.doc;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.avangard.qa.ibmobile.core.Confirm;
import ru.avangard.qa.ibmobile.core.Request;
import ru.avangard.qa.ibmobile.core.Session;
import ru.avangard.qa.ibmobile.core.Template;
import ru.avangard.qa.ibmobile.core.User;
import ru.avangard.qa.ibmobile.data.Accounts;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static ru.avangard.qa.ibmobile.api.Endpoint.PREPARE_DOC;
import static ru.avangard.qa.ibmobile.api.Endpoint.SEND_DOC;

@Epic("Переводы")
@Feature("ib_our_acc_transfer")
@Tag("mutating")
class IbOurAccTransferTest {

    private static final String DOC = "ib_our_acc_transfer";

    static Stream<Arguments> cases() {
        return Stream.of(
                arguments("С собственных средств, без подтверждения", User.MAIN, false, 1.0),
                arguments("С кредитного счёта, с подтверждением", User.MAIN, true, 1.0));
    }

    @DisplayName("Перевод между своими счетами")
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void transfer(String title, User user, boolean fromCredit, double amount) {
        try (var s = Session.login(user)) {
            List<String> debit = Accounts.rubDebitCardAccounts(s);
            List<String> from = fromCredit ? Accounts.rubCreditCardAccounts(s) : debit;
            assumeTrue(!from.isEmpty() && debit.size() >= 2, "Не хватает рублёвых карточных счетов у " + user);

            // случайные копейки: бэк отклоняет такой же перевод, если предыдущий ещё обрабатывается
            double sum = amount + ThreadLocalRandom.current().nextInt(1, 100) / 100.0;
            var content = Template.of(DOC)
                    .with("amount", sum)
                    .with("accDeb", from.get(0))
                    .with("accCred", debit.get(1));

            var prepared = Request.to(PREPARE_DOC).as(s)
                    .param("doc_type", DOC)
                    .param("doc_content", content)
                    .post().success().matchesSchema(DOC);
            assertThat(prepared.<Boolean>path("checkSuccess")).as("checkSuccess").isTrue();

            var sent = Request.to(SEND_DOC).as(s)
                    .param("secure", Confirm.secure(s, prepared))
                    .post().success().matchesSchema(DOC);
            assertThat(sent.<Number>path("doc.amount").doubleValue()).as("сумма").isCloseTo(sum, within(0.001));
        }
    }
}

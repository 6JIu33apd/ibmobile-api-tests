package ru.avangard.qa.ibmobile.tests.doc;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.avangard.qa.ibmobile.core.ApiResponse;
import ru.avangard.qa.ibmobile.core.BaseTest;
import ru.avangard.qa.ibmobile.core.Doc;
import ru.avangard.qa.ibmobile.core.Doc.Confirm;
import ru.avangard.qa.ibmobile.core.Login;
import ru.avangard.qa.ibmobile.core.Request;
import ru.avangard.qa.ibmobile.core.Section;
import ru.avangard.qa.ibmobile.core.Template;
import ru.avangard.qa.ibmobile.data.Accounts;
import ru.avangard.qa.ibmobile.data.Accounts.Kind;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static ru.avangard.qa.ibmobile.core.Endpoint.PREPARE_DOC;
import static ru.avangard.qa.ibmobile.core.ErrorCode.AUTHORIZATION_REQUIRED;
import static ru.avangard.qa.ibmobile.core.ErrorCode.INVALID_PARAMETERS;
import static ru.avangard.qa.ibmobile.core.Profile.MAIN;

@Epic(Section.PAYMENTS)
@Feature("Перевод между своими счетами")
@Tag("mutating")
@Login(MAIN)
@Execution(ExecutionMode.SAME_THREAD) // документ живёт в сессии; похожий перевод, пока обрабатывается предыдущий, — дубль
class IbOurAccTransferTest extends BaseTest {

    private static final String DOC = "ib_our_acc_transfer";

    /** Счета списания и зачисления по умолчанию: два рублёвых дебетовых. */
    private String from;
    private String to;

    @BeforeAll
    void accounts() {
        var debit = Accounts.of(session, Kind.DEBIT);
        assumeTrue(debit.size() >= 2, "Нужны два рублёвых дебетовых счёта");
        from = debit.get(0);
        to = debit.get(1);
    }

    static Stream<Arguments> transfers() {
        return Stream.of(
                arguments("с дебетового счёта, без подтверждения", Kind.DEBIT, Confirm.NONE),
                arguments("с кредитного счёта, подтверждение SMS и паролем", Kind.CREDIT, Confirm.SMSPWD));
    }

    @DisplayName("Перевод между своими счетами")
    @ParameterizedTest(name = "{0}")
    @MethodSource("transfers")
    void transfer(String title, Kind kind, Confirm confirm) {
        var accounts = Accounts.of(session, kind);
        assumeFalse(accounts.isEmpty(), "Нет счёта: " + kind);
        var amount = BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(101, 1000), 2); // та же сумма — дубль

        var prepared = prepare(content(accounts.getFirst(), to, amount)).success().matchesSchema(DOC);
        var sent = Doc.send(session, prepared, confirm).success().matchesSchema(DOC);
        assertThat(sent.decimal("doc.amount")).as("сумма").isEqualByComparingTo(amount);
    }

    @Story("Негативные")
    @DisplayName("Некорректная сумма отклоняется")
    @ParameterizedTest(name = "({0})")
    @ValueSource(strings = {"0", "-1.00"})
    void invalidAmount(BigDecimal amount) {
        prepare(content(from, to, amount)).error(INVALID_PARAMETERS);
    }

    @Story("Негативные")
    @DisplayName("Перевод без суммы отклоняется")
    @Test
    void withoutAmount() {
        prepare(content(from, to, BigDecimal.ONE).without("amount")).error(INVALID_PARAMETERS);
    }

    @Story("Негативные")
    @DisplayName("Перевод больше остатка отклоняется")
    @Test
    void insufficientFunds() {
        prepare(content(from, to, new BigDecimal("100000000.00"))).rejected("amountDeb", "Недостаточно средств на счете списания");
    }

    @Story("Негативные")
    @DisplayName("Перевод на тот же счёт отклоняется")
    @Test
    void sameAccount() {
        prepare(content(from, from, BigDecimal.ONE)).rejected("accCred", "Счет зачисления должен отличаться от счета списания");
    }

    @Story("Негативные")
    @DisplayName("Перевод с чужого счёта отклоняется")
    @Test
    void foreignAccount() {
        prepare(content("40817810000000000001", to, BigDecimal.ONE)).rejected("all", "Счет не найден");
    }

    @Story("Негативные")
    @DisplayName("Подготовка без ticket отклоняется")
    @Test
    void withoutTicket() {
        Request.to(PREPARE_DOC).param("doc_type", DOC).param("doc_content", content(from, to, BigDecimal.ONE))
                .post().error(AUTHORIZATION_REQUIRED);
    }

    private static Template content(String accDeb, String accCred, BigDecimal amount) {
        return Template.of(DOC).with("accDeb", accDeb).with("accCred", accCred).with("amount", amount);
    }

    private ApiResponse prepare(Template content) {
        return Doc.prepare(session, DOC, content);
    }
}

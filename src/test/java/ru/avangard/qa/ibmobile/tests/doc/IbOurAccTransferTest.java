package ru.avangard.qa.ibmobile.tests.doc;

import com.fasterxml.jackson.databind.node.ObjectNode;
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
import ru.avangard.qa.ibmobile.api.ApiResponse;
import ru.avangard.qa.ibmobile.api.Request;
import ru.avangard.qa.ibmobile.core.BaseTest;
import ru.avangard.qa.ibmobile.core.Doc;
import ru.avangard.qa.ibmobile.core.Doc.Confirm;
import ru.avangard.qa.ibmobile.data.AccListData;
import ru.avangard.qa.ibmobile.data.AccListData.AccountType;
import ru.avangard.qa.ibmobile.tests.Section;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static ru.avangard.qa.ibmobile.api.Endpoint.PREPARE_DOC;
import static ru.avangard.qa.ibmobile.api.ErrorCode.AUTHORIZATION_REQUIRED;
import static ru.avangard.qa.ibmobile.api.ErrorCode.INVALID_PARAMETERS;

/** Перевод между двумя своими счетами одной валюты; разные валюты — конвертация, отдельный документ. */
@Epic(Section.PAYMENTS)
@Feature("Перевод между своими счетами")
@Tag("mutating")
@Execution(ExecutionMode.SAME_THREAD) // документ живёт в сессии; похожий перевод, пока обрабатывается предыдущий, — дубль
class IbOurAccTransferTest extends BaseTest {

    private static final String DOC = "ib_our_acc_transfer";

    private AccListData accList;
    /** Перевод по умолчанию для негативных кейсов: с дебетового счёта на другой счёт той же валюты. */
    private List<String> pair;

    /** Один acc_list на класс; нет пары дебетовых счетов одной валюты — класс пропускается. */
    @BeforeAll
    void accounts() {
        accList = new AccListData(session());
        pair = pair(AccountType.DEBIT);
    }

    static Stream<Arguments> transfers() {
        return Stream.of(
                arguments("с дебетового счёта, без подтверждения", AccountType.DEBIT, Confirm.NONE),
                arguments("с кредитного счёта, подтверждение SMS и паролем", AccountType.CREDIT, Confirm.SMSPWD));
    }

    @DisplayName("Перевод между своими счетами")
    @ParameterizedTest(name = "{0}")
    @MethodSource("transfers")
    void transfer(String title, AccountType from, Confirm confirm) {
        var accounts = pair(from);
        var amount = BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(101, 1000), 2); // та же сумма — дубль

        var prepared = prepare(content(accounts.get(0), accounts.get(1), amount)).success().matchesSchema(DOC);
        var sent = Doc.send(session(), prepared, confirm).success().matchesSchema(DOC);
        assertThat(sent.decimal("doc.amount")).as("сумма").isEqualByComparingTo(amount);
    }

    /** Поле перевода по умолчанию → значение (null — без поля). */
    static Stream<Arguments> invalidParameters() {
        return Stream.of(
                arguments("нулевая сумма", "amount", 0),
                arguments("отрицательная сумма", "amount", new BigDecimal("-1.00")),
                arguments("без суммы", "amount", null));
    }

    @Story("Негативные")
    @DisplayName("Перевод с некорректными параметрами отклоняется")
    @ParameterizedTest(name = "({0})")
    @MethodSource("invalidParameters")
    void invalidParameters(String title, String field, Object value) {
        prepare(Doc.set(content(), field, value)).error(INVALID_PARAMETERS);
    }

    /** Поле перевода по умолчанию → значение, поле-виновник и сообщение отказа. Не static: нужны счета сессии. */
    Stream<Arguments> refusals() {
        return Stream.of(
                arguments("больше остатка", "amount", new BigDecimal("100000000.00"), "amountDeb", "Недостаточно средств на счете списания"),
                arguments("на тот же счёт", "accCred", pair.get(0), "accCred", "Счет зачисления должен отличаться от счета списания"),
                arguments("с чужого счёта", "accDeb", "40817810000000000001", "all", "Счет не найден"));
    }

    @Story("Негативные")
    @DisplayName("Перевод отклоняется проверками")
    @ParameterizedTest(name = "({0})")
    @MethodSource("refusals")
    void refusal(String title, String field, Object value, String badItem, String message) {
        prepare(Doc.set(content(), field, value)).rejected(badItem, message);
    }

    @Story("Негативные")
    @DisplayName("Подготовка без ticket отклоняется")
    @Test
    void withoutTicket() {
        Request.to(PREPARE_DOC).param("doc_type", DOC).param("doc_content", content()).post().error(AUTHORIZATION_REQUIRED);
    }

    /** Счёт типа from и другой счёт той же валюты; нет такой пары — кейс пропускается. */
    private List<String> pair(AccountType from) {
        var accounts = accList.accounts(from);
        assumeFalse(accounts.isEmpty(), "Нет счёта: " + from);
        var to = accList.sameCurrency(accounts.getFirst());
        assumeFalse(to.isEmpty(), "Нет второго счёта в валюте счёта " + accounts.getFirst());
        return List.of(accounts.getFirst(), to.getFirst());
    }

    private ObjectNode content() {
        return content(pair.get(0), pair.get(1), BigDecimal.ONE);
    }

    private static ObjectNode content(String accDeb, String accCred, BigDecimal amount) {
        return Doc.content(DOC).put("accDeb", accDeb).put("accCred", accCred).put("amount", amount);
    }

    private ApiResponse prepare(ObjectNode content) {
        return Doc.prepare(session(), DOC, content);
    }
}

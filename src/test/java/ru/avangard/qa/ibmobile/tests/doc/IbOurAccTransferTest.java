package ru.avangard.qa.ibmobile.tests.doc;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.avangard.qa.ibmobile.api.DocType;
import ru.avangard.qa.ibmobile.api.Request;
import ru.avangard.qa.ibmobile.core.BaseTest;
import ru.avangard.qa.ibmobile.core.Confirm;
import ru.avangard.qa.ibmobile.core.Template;
import ru.avangard.qa.ibmobile.data.AccListData;
import ru.avangard.qa.ibmobile.data.AccListData.AccountType;
import ru.avangard.qa.ibmobile.tests.Section;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static ru.avangard.qa.ibmobile.api.Endpoint.PREPARE_DOC;
import static ru.avangard.qa.ibmobile.api.Endpoint.SEND_DOC;
import static ru.avangard.qa.ibmobile.api.ErrorCode.AUTHORIZATION_REQUIRED;
import static ru.avangard.qa.ibmobile.api.ErrorCode.INVALID_PARAMETERS;

/** Перевод между двумя своими счетами одной валюты; разные валюты — конвертация, отдельный документ. */
@Epic(Section.PAYMENTS)
@Feature("Перевод между своими счетами")
@Tag("mutating")
@Execution(ExecutionMode.SAME_THREAD) // документ живёт в сессии; похожий перевод, пока обрабатывается предыдущий, — дубль
class IbOurAccTransferTest extends BaseTest {

    private static final DocType DOC_TYPE = DocType.IB_OUR_ACC_TRANSFER;

    static Stream<Arguments> transfers() {
        return Stream.of(
                arguments("с дебетового счёта, без подтверждения", AccountType.DEBIT, Confirm.NONE),
                arguments("с кредитного счёта, подтверждение SMS и паролем", AccountType.CREDIT, Confirm.SMSPWD));
    }

    @DisplayName("Перевод между своими счетами")
    @ParameterizedTest(name = "{0}")
    @MethodSource("transfers")
    void transfer(String title, AccountType from, Confirm confirm) {
        var amount = BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(101, 1000), 2); // та же сумма — дубль
        var prepared = Request.to(PREPARE_DOC).as(session()).param("doc_type", DOC_TYPE)
                .param("doc_content", content(from, amount))
                .post().success().matchesSchema(DOC_TYPE);
        var sent = Request.to(SEND_DOC).as(session()).param("secure", confirm.secure(session(), prepared))
                .post().success().matchesSchema(DOC_TYPE);
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
        Request.to(PREPARE_DOC).as(session()).param("doc_type", DOC_TYPE)
                .param("doc_content", Template.set(content(), field, value))
                .post().error(INVALID_PARAMETERS);
    }

    /** Поле перевода по умолчанию → значение, поле-виновник и сообщение отказа. */
    static Stream<Arguments> rejections() {
        return Stream.of(
                arguments("больше остатка", "amount", new BigDecimal("100000000.00"), "amountDeb", "Недостаточно средств на счете списания"),
                arguments("с чужого счёта", "accDeb", "40817810000000000001", "all", "Счет не найден"));
    }

    @Story("Негативные")
    @DisplayName("Перевод отклоняется проверками")
    @ParameterizedTest(name = "({0})")
    @MethodSource("rejections")
    void rejection(String title, String field, Object value, String badItem, String message) {
        Request.to(PREPARE_DOC).as(session()).param("doc_type", DOC_TYPE)
                .param("doc_content", Template.set(content(), field, value))
                .post().rejected(badItem, message);
    }

    /** Не в таблице: значение зависит от счетов сессии. */
    @Story("Негативные")
    @DisplayName("Перевод на тот же счёт отклоняется")
    @Test
    void sameAccount() {
        var content = content();
        Request.to(PREPARE_DOC).as(session()).param("doc_type", DOC_TYPE)
                .param("doc_content", Template.set(content, "accCred", content.get("accDeb").asText()))
                .post().rejected("accCred", "Счет зачисления должен отличаться от счета списания");
    }

    @Story("Негативные")
    @DisplayName("Подготовка без ticket отклоняется")
    @Test
    void withoutTicket() {
        Request.to(PREPARE_DOC).param("doc_type", DOC_TYPE)
                .param("doc_content", content()).post().error(AUTHORIZATION_REQUIRED);
    }

    /** Перевод по умолчанию: 1.00 с дебетового счёта. */
    private ObjectNode content() {
        return content(AccountType.DEBIT, BigDecimal.ONE);
    }

    /** Перевод amount со счёта типа from на другой счёт той же валюты; нет такой пары — кейс пропускается. */
    private ObjectNode content(AccountType from, BigDecimal amount) {
        var accList = new AccListData(session());
        var accounts = accList.accounts(from);
        assumeFalse(accounts.isEmpty(), "Нет счёта: " + from);
        var to = accList.sameCurrency(accounts.getFirst());
        assumeFalse(to.isEmpty(), "Нет второго счёта в валюте счёта " + accounts.getFirst());
        return Template.of(DOC_TYPE).put("accDeb", accounts.getFirst()).put("accCred", to.getFirst()).put("amount", amount);
    }
}

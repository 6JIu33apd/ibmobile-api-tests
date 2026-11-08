package ru.avangard.qa.ibmobile.tests.doc;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.avangard.qa.ibmobile.api.DocType;
import ru.avangard.qa.ibmobile.api.Request;
import ru.avangard.qa.ibmobile.core.AuthMethod;
import ru.avangard.qa.ibmobile.core.BaseTest;
import ru.avangard.qa.ibmobile.core.DocContent;
import ru.avangard.qa.ibmobile.core.Negative;
import ru.avangard.qa.ibmobile.data.AccListData;
import ru.avangard.qa.ibmobile.tests.Section;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static ru.avangard.qa.ibmobile.api.Endpoint.PREPARE_DOC;
import static ru.avangard.qa.ibmobile.api.Endpoint.SEND_DOC;
import static ru.avangard.qa.ibmobile.api.ErrorCode.AUTHORIZATION_REQUIRED;
import static ru.avangard.qa.ibmobile.api.ErrorCode.INVALID_PARAMETERS;

/**
 * Перевод между двумя своими счетами одной валюты; разные валюты — конвертация, отдельный документ.
 * Подготовленный документ бэк хранит общим для всех ticket логина: документы идут по одному.
 */
@ResourceLock("prepared_doc")
@Epic(Section.PAYMENTS)
@Feature("Перевод между своими счетами")
class IbOurAccTransferTest extends BaseTest {

    private static final DocType DOC_TYPE = DocType.IB_OUR_ACC_TRANSFER;
    /** Счета списания — условия по полям acc_list. */
    private static final String DEBIT = "it.cardAcc && !it.isCredit && it.currency == 'RUR'";
    private static final String CREDIT = "it.isCredit && it.currency == 'RUR'";

    static Stream<Arguments> transfers() {
        return Stream.of(
                argumentSet("с рублёвого дебетового счёта, без подтверждения", DEBIT, AuthMethod.NONE),
                argumentSet("с рублёвого кредитного счёта, подтверждение SMS и паролем", CREDIT, AuthMethod.SMSPWD));
    }

    @DisplayName("Перевод между своими счетами")
    @ParameterizedTest(name = "{argumentSetName}")
    @MethodSource("transfers")
    void transfer(String accDebCondition, AuthMethod authMethod) {
        var amount = randomAmount();
        var docContent = docContent(accDebCondition, amount);
        var prepared = Request.to(PREPARE_DOC).as(session()).doc(DOC_TYPE, docContent)
                .post().success().matchesSchema(DOC_TYPE);
        var sent = Request.to(SEND_DOC).as(session()).param("secure", authMethod.secure(session(), prepared))
                .post().success().matchesSchema(DOC_TYPE);
        assertThat(sent.decimal("doc.amount")).as("сумма").isEqualByComparingTo(amount);
        assertThat(sent.<String>path("doc.accDeb")).as("счёт списания").isEqualTo(docContent.get("accDeb").asText());
        assertThat(sent.<String>path("doc.accCred")).as("счёт зачисления").isEqualTo(docContent.get("accCred").asText());
    }

    @Nested
    @Negative
    class Negatives {

        /** Поле перевода по умолчанию → значение (null — без поля). */
        static Stream<Arguments> invalidParameters() {
            return Stream.of(
                    argumentSet("нулевая сумма", "amount", 0),
                    argumentSet("отрицательная сумма", "amount", new BigDecimal("-1.00")),
                    argumentSet("без суммы", "amount", null));
        }

        @DisplayName("Перевод с некорректными параметрами отклоняется")
        @ParameterizedTest(name = "({argumentSetName})")
        @MethodSource("invalidParameters")
        void invalidParameters(String field, Object value) {
            Request.to(PREPARE_DOC).as(session()).doc(DOC_TYPE, DocContent.set(docContent(), field, value))
                    .post().error(INVALID_PARAMETERS);
        }

        /** Поле перевода по умолчанию → значение, поле-виновник и сообщение отказа. */
        static Stream<Arguments> rejections() {
            return Stream.of(
                    argumentSet("больше остатка", "amount", new BigDecimal("100000000.00"), "amountDeb", "Недостаточно средств на счете списания"),
                    argumentSet("с чужого счёта", "accDeb", "40817810000000000001", "all", "Счет не найден"),
                    argumentSet("на чужой счёт", "accCred", "40817810000000000001", "all", "Счет не найден"));
        }

        @DisplayName("Перевод отклоняется проверками")
        @ParameterizedTest(name = "({argumentSetName})")
        @MethodSource("rejections")
        void rejection(String field, Object value, String badItem, String errorMessage) {
            Request.to(PREPARE_DOC).as(session()).doc(DOC_TYPE, DocContent.set(docContent(), field, value))
                    .post().rejected(badItem, errorMessage);
        }

        /** Не в таблице: значение зависит от счетов сессии. */
        @DisplayName("Перевод на тот же счёт отклоняется")
        @Test
        void sameAccount() {
            var docContent = docContent();
            Request.to(PREPARE_DOC).as(session()).doc(DOC_TYPE, DocContent.set(docContent, "accCred", docContent.get("accDeb").asText()))
                    .post().rejected("accCred", "Счет зачисления должен отличаться от счета списания");
        }

        @DisplayName("Подготовка без ticket отклоняется")
        @Test
        void withoutTicket() {
            Request.to(PREPARE_DOC).doc(DOC_TYPE, docContent()).post().error(AUTHORIZATION_REQUIRED);
        }

        @DisplayName("Отправка без ticket отклоняется")
        @Test
        void sendWithoutTicket() {
            var prepared = Request.to(PREPARE_DOC).as(session()).doc(DOC_TYPE, docContent()).post().success();
            Request.to(SEND_DOC).param("secure", AuthMethod.NONE.secure(session(), prepared)).post().error(AUTHORIZATION_REQUIRED);
        }

        @DisplayName("Повторная отправка документа отклоняется")
        @Test
        void sentTwice() {
            var prepared = Request.to(PREPARE_DOC).as(session()).doc(DOC_TYPE, docContent(DEBIT, randomAmount())).post().success();
            var secure = AuthMethod.NONE.secure(session(), prepared);
            Request.to(SEND_DOC).as(session()).param("secure", secure).post().success();
            Request.to(SEND_DOC).as(session()).param("secure", secure).post().error(INVALID_PARAMETERS);
        }
    }

    /** Случайная сумма снижает вероятность отказа-дубля. */
    private static BigDecimal randomAmount() {
        return BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(101, 1000), 2);
    }

    /** Перевод по умолчанию: 1.00 с рублёвого дебетового счёта. */
    private ObjectNode docContent() {
        return docContent(DEBIT, BigDecimal.ONE);
    }

    /** Перевод amount со счёта по условию accDebCondition на другой счёт той же валюты; нет такой пары — кейс пропускается. */
    private ObjectNode docContent(String accDebCondition, BigDecimal amount) {
        var accList = new AccListData(session());
        var accDeb = accList.codes(accDebCondition).getFirst();
        var accCred = accList.codes("it.currency == '" + accList.field(accDeb, "currency") + "' && it.code != '" + accDeb + "'").getFirst();
        return DocContent.of(DOC_TYPE).put("accDeb", accDeb).put("accCred", accCred).put("amount", amount);
    }
}

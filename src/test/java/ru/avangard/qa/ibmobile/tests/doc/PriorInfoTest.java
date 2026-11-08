package ru.avangard.qa.ibmobile.tests.doc;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.avangard.qa.ibmobile.api.DocType;
import ru.avangard.qa.ibmobile.api.Request;
import ru.avangard.qa.ibmobile.core.BaseTest;
import ru.avangard.qa.ibmobile.core.Negative;
import ru.avangard.qa.ibmobile.tests.Section;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static ru.avangard.qa.ibmobile.api.Endpoint.PRIOR_INFO;
import static ru.avangard.qa.ibmobile.api.ErrorCode.AUTHORIZATION_REQUIRED;
import static ru.avangard.qa.ibmobile.api.ErrorCode.INVALID_PARAMETERS;

/** Данные для заполнения документа; у каждого doc_type свой ответ и своя схема. */
@Epic(Section.REFS)
@Feature("Данные для документа")
class PriorInfoTest extends BaseTest {

    static Stream<Arguments> docTypes() {
        return Stream.of(
                argumentSet("внешний рублёвый перевод", DocType.IB_PAY_RUB, "otherPersonDocumentList"),
                argumentSet("внутренний рублёвый платёж", DocType.IB_INSIDE_PAY_RUB, "otherPersonDocumentList"),
                argumentSet("заявление о несогласии с операцией", DocType.IB_CARD_CHARGEBACK_STATEMENT, "accountList.cards.flatten().id"),
                argumentSet("заявление на выпуск карты", DocType.IB_CARD_ORDER_STATEMENT, "availableAccounts"));
    }

    @DisplayName("Данные для документа")
    @ParameterizedTest(name = "({argumentSetName})")
    @MethodSource("docTypes")
    void priorInfo(DocType docType, String listPath) {
        var priorInfo = Request.to(PRIOR_INFO).as(session()).param("doc_type", docType).post().success().matchesSchema(docType);
        assertThat(priorInfo.<List<?>>path(listPath)).as(listPath).isNotEmpty();
        if (docType == DocType.IB_CARD_CHARGEBACK_STATEMENT) {
            assertThat(priorInfo.<List<Integer>>path("chargebackReasons.reasonType")).as("причины несогласия")
                    .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7);
        }
    }

    @Nested
    @Negative
    class Negatives {

        /** Параметр запроса по умолчанию → значение (null — без параметра). */
        static Stream<Arguments> invalidParameters() {
            return Stream.of(
                    argumentSet("без doc_type", "doc_type", null),
                    argumentSet("неизвестный doc_type", "doc_type", "no_such_doc"));
        }

        @DisplayName("Запрос с некорректными параметрами отклоняется")
        @ParameterizedTest(name = "({argumentSetName})")
        @MethodSource("invalidParameters")
        void invalidParameters(String param, Object value) {
            Request.to(PRIOR_INFO).as(session()).param("doc_type", DocType.IB_PAY_RUB).param(param, value)
                    .post().error(INVALID_PARAMETERS);
        }

        @DisplayName("Запрос без ticket отклоняется")
        @Test
        void withoutTicket() {
            Request.to(PRIOR_INFO).param("doc_type", DocType.IB_PAY_RUB).post().error(AUTHORIZATION_REQUIRED);
        }
    }
}

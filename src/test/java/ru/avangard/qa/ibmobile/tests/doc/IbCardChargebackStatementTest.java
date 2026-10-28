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
import ru.avangard.qa.ibmobile.tests.Section;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static ru.avangard.qa.ibmobile.api.Endpoint.PREPARE_DOC;
import static ru.avangard.qa.ibmobile.api.Endpoint.PRIOR_INFO;
import static ru.avangard.qa.ibmobile.api.Endpoint.SEND_DOC;
import static ru.avangard.qa.ibmobile.api.ErrorCode.AUTHORIZATION_REQUIRED;

/**
 * Заявление о несогласии с операцией по карте: карта — из prior_info, операция — из шаблона doc_content.
 * Подготовленный документ бэк хранит общим для всех ticket логина: документы идут по одному.
 */
@ResourceLock("prepared_doc")
@Epic(Section.STATEMENTS)
@Feature("Заявление о несогласии с операцией по карте")
class IbCardChargebackStatementTest extends BaseTest {

    private static final DocType DOC_TYPE = DocType.IB_CARD_CHARGEBACK_STATEMENT;
    private static final String FILE = "image.png";

    /** reasonType — причина из chargebackReasons prior_info; у каждой свой список документов. */
    static Stream<Arguments> reasons() {
        return Stream.of(
                argumentSet("не согласен с суммой списания", 1),
                argumentSet("не совершал операцию", 2),
                argumentSet("сумма списана дважды", 3),
                argumentSet("возврат товара не зачислен", 4),
                argumentSet("товар не доставлен или услуга не оказана", 5),
                argumentSet("оплачено другим способом", 6),
                argumentSet("иное", 7));
    }

    @DisplayName("Заявление о несогласии с операцией по карте, подтверждение скретч-кодом")
    @ParameterizedTest(name = "({argumentSetName})")
    @MethodSource("reasons")
    void statement(int reasonType) {
        var docContent = docContent().put("reasonType", reasonType);
        var prepared = Request.to(PREPARE_DOC).as(session()).doc(DOC_TYPE, docContent, FILE)
                .post().success().matchesSchema(DOC_TYPE);
        assertThat(prepared.<Long>path("doc.cardId")).as("карта").isEqualTo(docContent.get("cardId").asLong());
        assertThat(prepared.<Integer>path("doc.reasonType")).as("причина").isEqualTo(reasonType);
        assertThat(prepared.<List<String>>path("doc.attachmentsList.filename")).as("вложения").containsExactly(FILE);
        Request.to(SEND_DOC).as(session()).param("secure", AuthMethod.SCRETCH.secure(session(), prepared))
                .post().success().matchesSchema(DOC_TYPE);
    }

    @Nested
    @Negative
    class Negatives {

        /** Поле заявления по умолчанию (с вложением) → значение (null — без поля), поле-виновник и сообщение отказа. */
        static Stream<Arguments> rejections() {
            return Stream.of(
                    argumentSet("пустое описание", "description", "", "all", "Заполните description"),
                    argumentSet("без описания", "description", null, "all", "Заполните description"),
                    argumentSet("без карты", "cardId", null, "all", "Заполните cardId"),
                    argumentSet("пустой список операций", "opersList", List.of(), "all", "Укажите хотя бы одну операцию"),
                    argumentSet("без списка операций", "opersList", null, "all", "Укажите хотя бы одну операцию"));
        }

        @DisplayName("Заявление отклоняется проверками")
        @ParameterizedTest(name = "({argumentSetName})")
        @MethodSource("rejections")
        void rejection(String field, Object value, String badItem, String errorMessage) {
            Request.to(PREPARE_DOC).as(session()).doc(DOC_TYPE, DocContent.set(docContent(), field, value), FILE)
                    .post().rejected(badItem, errorMessage);
        }

        @DisplayName("Заявление без вложения отклоняется")
        @Test
        void withoutAttachment() {
            Request.to(PREPARE_DOC).as(session()).doc(DOC_TYPE, docContent())
                    .post().rejected("all", "Прикрепление документов обязательно");
        }

        @DisplayName("Вложение без файла отклоняется")
        @Test
        void attachmentWithoutFile() {
            var attachmentsList = List.of(Map.of("filename", FILE, "contentType", "image/png"));
            Request.to(PREPARE_DOC).as(session()).doc(DOC_TYPE, DocContent.set(docContent(), "attachmentsList", attachmentsList))
                    .post().rejected("lobFile", "Ошибка обработки файла-вложения");
        }

        /** Сейчас бэк отвечает errorCode 2 — тест падает как ошибка сервера. */
        @DisplayName("Заявление по несуществующей операции отклоняется")
        @Test
        void unknownOperation() {
            var prepared = Request.to(PREPARE_DOC).as(session()).doc(DOC_TYPE, DocContent.set(docContent(), "opersList", List.of(1)), FILE).post();
            assertThat(prepared.<String>path("resultCode")).as("отказ проверок").isEqualTo("FAILED");
        }

        @DisplayName("Подготовка без ticket отклоняется")
        @Test
        void withoutTicket() {
            Request.to(PREPARE_DOC).doc(DOC_TYPE, docContent(), FILE).post().error(AUTHORIZATION_REQUIRED);
        }
    }

    /** Заявление по умолчанию: первая карта из prior_info, операция и причина — из шаблона. */
    private ObjectNode docContent() {
        List<Long> cardIds = Request.to(PRIOR_INFO).as(session()).param("doc_type", DOC_TYPE).post().success()
                .path("accountList.cards.flatten().id");
        return DocContent.of(DOC_TYPE).put("cardId", cardIds.getFirst()).put("description", "Заявление автотеста о несогласии с операцией");
    }
}

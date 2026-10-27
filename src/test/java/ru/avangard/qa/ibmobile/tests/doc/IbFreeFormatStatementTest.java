package ru.avangard.qa.ibmobile.tests.doc;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.avangard.qa.ibmobile.api.DocType;
import ru.avangard.qa.ibmobile.api.Request;
import ru.avangard.qa.ibmobile.core.BaseTest;
import ru.avangard.qa.ibmobile.core.Confirm;
import ru.avangard.qa.ibmobile.core.Template;
import ru.avangard.qa.ibmobile.tests.Section;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static ru.avangard.qa.ibmobile.api.Endpoint.PREPARE_DOC;
import static ru.avangard.qa.ibmobile.api.Endpoint.SEND_DOC;
import static ru.avangard.qa.ibmobile.api.ErrorCode.AUTHORIZATION_REQUIRED;

/** Подготовленный документ бэк хранит общим для всех ticket логина: документы идут по одному. */
@ResourceLock("prepared_doc")
@Epic(Section.STATEMENTS)
@Feature("Заявление в свободной форме")
class IbFreeFormatStatementTest extends BaseTest {

    private static final DocType DOC_TYPE = DocType.IB_FREE_FORMAT_STATEMENT;
    private static final String TEXT = "Тестовое заявление автотеста";

    static Stream<Arguments> statements() {
        return Stream.of(
                arguments("без вложения", List.of()),
                arguments("с вложением", List.of("image.png")));
    }

    @DisplayName("Заявление в свободной форме")
    @ParameterizedTest(name = "{0}")
    @MethodSource("statements")
    void statement(String title, List<String> files) {
        var prepared = Request.to(PREPARE_DOC).as(session()).doc(DOC_TYPE, content(), files.toArray(String[]::new))
                .post().success().matchesSchema(DOC_TYPE);
        assertThat(prepared.<String>path("doc.text")).as("текст заявления").isEqualTo(TEXT);
        assertThat(prepared.<List<String>>path("doc.attachmentsList.filename")).as("вложения").isEqualTo(files);
        Request.to(SEND_DOC).as(session()).param("secure", Confirm.NONE.secure(session(), prepared))
                .post().success().matchesSchema(DOC_TYPE);
    }

    /** Поле заявления по умолчанию → значение (null — без поля), поле-виновник и сообщение отказа. */
    static Stream<Arguments> rejections() {
        return Stream.of(
                arguments("пустой текст", "text", "", "text", "Заполните текст заявления"),
                arguments("без текста", "text", null, "text", "Заполните текст"),
                arguments("вложение без файла", "attachmentsList", List.of(Map.of("filename", "image.png", "contentType", "image/png")),
                        "lobFile", "Ошибка обработки файла-вложения"));
    }

    @Story("Негативные")
    @DisplayName("Заявление отклоняется проверками")
    @ParameterizedTest(name = "({0})")
    @MethodSource("rejections")
    void rejection(String title, String field, Object value, String badItem, String message) {
        Request.to(PREPARE_DOC).as(session()).doc(DOC_TYPE, Template.set(content(), field, value))
                .post().rejected(badItem, message);
    }

    @Story("Негативные")
    @DisplayName("Подготовка без ticket отклоняется")
    @Test
    void withoutTicket() {
        Request.to(PREPARE_DOC).doc(DOC_TYPE, content()).post().error(AUTHORIZATION_REQUIRED);
    }

    /** Заявление по умолчанию: текст TEXT, без вложений. */
    private static ObjectNode content() {
        return Template.of(DOC_TYPE).put("text", TEXT);
    }
}

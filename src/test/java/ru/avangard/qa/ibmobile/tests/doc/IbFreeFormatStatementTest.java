package ru.avangard.qa.ibmobile.tests.doc;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.avangard.qa.ibmobile.api.ApiResponse;
import ru.avangard.qa.ibmobile.core.BaseTest;
import ru.avangard.qa.ibmobile.core.Doc;
import ru.avangard.qa.ibmobile.core.Doc.Confirm;
import ru.avangard.qa.ibmobile.tests.Section;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@Epic(Section.STATEMENTS)
@Feature("Заявление в свободной форме")
@Tag("mutating")
@Execution(ExecutionMode.SAME_THREAD) // документ живёт в сессии
class IbFreeFormatStatementTest extends BaseTest {

    private static final String DOC = "ib_free_format_statement";
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
        var prepared = prepare(content(TEXT), files.toArray(String[]::new)).success().matchesSchema(DOC);
        assertThat(prepared.<String>path("doc.text")).as("текст заявления").isEqualTo(TEXT);
        assertThat(prepared.<List<String>>path("doc.attachmentsList.filename")).as("вложения").isEqualTo(files);

        Doc.send(session(), prepared, Confirm.NONE).success().matchesSchema(DOC);
    }

    /** Поле заявления по умолчанию → значение (null — без поля), поле-виновник и сообщение отказа. */
    static Stream<Arguments> refusals() {
        return Stream.of(
                arguments("пустой текст", "text", "", "text", "Заполните текст заявления"),
                arguments("без текста", "text", null, "text", "Заполните текст"),
                arguments("вложение без файла", "attachmentsList", List.of(Map.of("filename", "image.png", "contentType", "image/png")),
                        "lobFile", "Ошибка обработки файла-вложения"));
    }

    @Story("Негативные")
    @DisplayName("Заявление отклоняется проверками")
    @ParameterizedTest(name = "({0})")
    @MethodSource("refusals")
    void refusal(String title, String field, Object value, String badItem, String message) {
        prepare(Doc.set(content(TEXT), field, value)).rejected(badItem, message);
    }

    private static ObjectNode content(String text) {
        return Doc.content(DOC).put("text", text);
    }

    private ApiResponse prepare(ObjectNode content, String... files) {
        return Doc.prepare(session(), DOC, content, files);
    }
}

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
import ru.avangard.qa.ibmobile.api.ApiResponse;
import ru.avangard.qa.ibmobile.core.BaseTest;
import ru.avangard.qa.ibmobile.core.Doc;
import ru.avangard.qa.ibmobile.core.Doc.Confirm;
import ru.avangard.qa.ibmobile.tests.Section;

import java.util.List;
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

    @Story("Негативные")
    @DisplayName("Заявление с пустым текстом отклоняется")
    @Test
    void emptyText() {
        prepare(content("")).rejected("text", "Заполните текст заявления");
    }

    @Story("Негативные")
    @DisplayName("Заявление без текста отклоняется")
    @Test
    void withoutText() {
        prepare(content(TEXT).without("text")).rejected("text", "Заполните текст");
    }

    @Story("Негативные")
    @DisplayName("Заявление с неприложенным вложением отклоняется")
    @Test
    void missingAttachment() {
        var content = content(TEXT);
        content.putArray("attachmentsList").addObject().put("filename", "image.png").put("contentType", "image/png");
        prepare(content).rejected("lobFile", "Ошибка обработки файла-вложения");
    }

    private static ObjectNode content(String text) {
        return Doc.content(DOC).put("text", text);
    }

    private ApiResponse prepare(ObjectNode content, String... files) {
        return Doc.prepare(session(), DOC, content, files);
    }
}

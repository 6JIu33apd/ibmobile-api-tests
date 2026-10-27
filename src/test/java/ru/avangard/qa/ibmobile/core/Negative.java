package ru.avangard.qa.ibmobile.core;

import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Вложенный класс негативных кейсов ручки: {@code @Nested @Negative class Negatives { ... }} внутри класса теста.
 * {@code @Nested} — явно на классе: JUnit нашёл бы его и в мета-аннотации, а инспекция IDEA — нет.
 * Story «Негативные» в Allure — всем его тестам; Epic, Feature, контур и блокировки — от внешнего класса.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Story("Негативные")
@DisplayName("Негативные")
public @interface Negative {
}

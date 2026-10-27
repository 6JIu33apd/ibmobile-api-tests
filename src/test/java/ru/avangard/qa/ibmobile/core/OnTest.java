package ru.avangard.qa.ibmobile.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Тест (метод или класс) идёт на контуре test. Там одна сессия на логин — такие тесты идут по одному. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@ResourceLock("test")
@Tag("test")
public @interface OnTest {
}

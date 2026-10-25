package ru.avangard.qa.ibmobile.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Нестандартный профиль для сессии {@link BaseTest} (по умолчанию — {@link Profile#MAIN}). */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Login {
    Profile value();
}

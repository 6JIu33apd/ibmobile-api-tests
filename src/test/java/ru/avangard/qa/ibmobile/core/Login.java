package ru.avangard.qa.ibmobile.core;

import ru.avangard.qa.ibmobile.config.Profile;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Профиль сессии теста, если не {@link Profile#MAIN}: {@code @Login(Profile.X)} на методе. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Login {
    Profile value();
}

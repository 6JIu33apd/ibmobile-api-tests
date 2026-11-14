package ru.avangard.qa.ibmobile.core;

import io.restassured.path.json.JsonPath;
import java.util.*;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Именованные ответы помощников одного кейса. */
public final class TestContext {
    private final Map<String, JsonPath> responses = new HashMap<>();

    public void put(String name, JsonPath response) { responses.put(name, response); }

    public JsonPath get(String name) { return responses.get(name); }

    /** GPath выбирает элемент; если его нет, кейс пропускается. */
    public <T> T find(String name, String path) {
        T value = get(name).get(path);
        assumeTrue(value != null, "Нет данных: " + name + " / " + path);
        return value;
    }
}

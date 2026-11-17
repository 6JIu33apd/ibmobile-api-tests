package ru.avangard.qa.ibmobile.config;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Настройки выбранного контура; каждый файл контура читается один раз.
 */
public final class Config {
    private static final ThreadLocal<String> ENV = new ThreadLocal<>();
    private static final Map<String, Properties> CACHE = new ConcurrentHashMap<>();

    private Config() {
    }

    public static void setEnv(String env) {
        ENV.set(env);
    }

    public static Properties settings() {
        return CACHE.computeIfAbsent(ENV.get(), env -> load(Path.of(System.getProperty("user.home"), ".ibmobile", env + ".properties").toUri().toString()));
    }

    public static String get(String key) {
        return Objects.requireNonNull(settings().getProperty(key));
    }

    public static Properties load(String source) {
        try (var in = new InputStreamReader(URI.create(source).toURL().openStream(), UTF_8.newDecoder())) {
            Properties properties = new Properties();
            properties.load(in);
            properties.values().removeIf(value -> value.toString().isBlank());
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException(source, e);
        }
    }
}

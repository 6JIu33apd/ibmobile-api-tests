package ru.avangard.qa.ibmobile.core;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Конфиг прогона: -Denv=dev|test|prod, -Drelease=&lt;релиз бэка&gt;.
 * Параметр ищется в ~/.ibmobile/secrets.properties (ключи &lt;env&gt;.*), env/&lt;env&gt;.properties, config.properties.
 */
public final class Config {

    private static final Properties DEFAULTS = load(resource("config.properties"), null);
    public static final String ENV = System.getProperty("env", DEFAULTS.getProperty("env"));
    public static final String RELEASE = System.getProperty("release", DEFAULTS.getProperty("release"));

    private static final Properties PROPS = secrets(load(resource("env/" + ENV + ".properties"), DEFAULTS));
    private static final Properties VERSIONS = load(resource("versions/" + RELEASE + ".properties"), null);

    private Config() {
    }

    public static String get(String key) {
        return required(PROPS, key);
    }

    /** null, если не задан. */
    public static String find(String key) {
        String value = PROPS.getProperty(key, "").trim();
        return value.isEmpty() ? null : value;
    }

    public static String version(Endpoint endpoint) {
        return required(VERSIONS, endpoint.name());
    }

    private static String required(Properties props, String key) {
        String value = props.getProperty(key, "").trim();
        if (value.isEmpty()) {
            throw new IllegalStateException("Не задан " + key + " (env=" + ENV + ", release=" + RELEASE + ")");
        }
        return value;
    }

    /** Секреты текущего контура без префикса &lt;env&gt;. поверх остального конфига. */
    private static Properties secrets(Properties defaults) {
        Path path = Path.of(System.getProperty("user.home"), ".ibmobile", "secrets.properties");
        Properties all;
        try {
            all = load(Files.exists(path) ? Files.newBufferedReader(path, UTF_8) : new StringReader(""), null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Properties props = new Properties(defaults);
        all.stringPropertyNames().stream()
                .filter(key -> key.startsWith(ENV + "."))
                .forEach(key -> props.setProperty(key.substring(ENV.length() + 1), all.getProperty(key)));
        return props;
    }

    private static Reader resource(String name) {
        var in = Config.class.getResourceAsStream("/" + name);
        if (in == null) {
            throw new IllegalStateException("Нет " + name);
        }
        return new InputStreamReader(in, UTF_8);
    }

    private static Properties load(Reader reader, Properties defaults) {
        try (reader) {
            Properties props = new Properties(defaults);
            props.load(reader);
            return props;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

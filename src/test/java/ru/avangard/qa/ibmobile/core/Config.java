package ru.avangard.qa.ibmobile.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Вся конфигурация прогона:
 * config.properties (по умолчанию) → env/&lt;env&gt;.properties, users/&lt;env&gt;.properties,
 * versions/&lt;release&gt;.properties и ~/.ibmobile/secrets.properties.
 * Контур и релиз переключаются через -Denv=dev|test|prod и -Drelease=&lt;номер релиза&gt;.
 */
public final class Config {

    public static final Path SECRETS = Path.of(System.getProperty("user.home"), ".ibmobile", "secrets.properties");

    private static final Properties DEFAULTS = classpath("config.properties");
    private static final String ENV = System.getProperty("env", DEFAULTS.getProperty("env"));
    private static final String RELEASE = System.getProperty("release", DEFAULTS.getProperty("release"));

    private static final Properties ENV_PROPS = classpath("env/" + ENV + ".properties");
    private static final Properties USERS = classpath("users/" + ENV + ".properties");
    private static final Properties VERSIONS = classpath("versions/" + RELEASE + ".properties");
    private static final Properties SECRET_PROPS = secrets();

    private Config() {
    }

    public static String env() {
        return ENV;
    }

    public static String release() {
        return RELEASE;
    }

    public static boolean isDev() {
        return "dev".equals(ENV);
    }

    public static boolean isProd() {
        return "prod".equals(ENV);
    }

    /** Обязательный параметр контура из env/&lt;env&gt;.properties. */
    public static String get(String key) {
        String value = find(key);
        if (value == null) {
            throw new IllegalStateException("Нет параметра '" + key + "' в env/" + ENV + ".properties");
        }
        return value;
    }

    /** Необязательный параметр контура, null если не задан. */
    public static String find(String key) {
        return blankToNull(ENV_PROPS.getProperty(key));
    }

    public static String baseUrl() {
        return get("baseUrl");
    }

    public static String uid() {
        return get("uid");
    }

    public static int threads() {
        return Integer.parseInt(get("threads"));
    }

    /** Логин пользователя на текущем контуре, null если не задан. */
    public static String login(User user) {
        return blankToNull(USERS.getProperty(user.name()));
    }

    /** Версия ручки в текущем релизе. */
    public static String version(String endpoint) {
        String version = blankToNull(VERSIONS.getProperty(endpoint));
        if (version == null) {
            throw new IllegalStateException("Нет версии " + endpoint + " в versions/" + RELEASE + ".properties");
        }
        return version;
    }

    /** Секрет текущего контура: ключ в secrets.properties записывается как &lt;env&gt;.&lt;key&gt;. */
    public static String secret(String key) {
        String value = blankToNull(SECRET_PROPS.getProperty(ENV + "." + key));
        if (value == null) {
            throw new IllegalStateException("Нет '" + ENV + "." + key + "' в " + SECRETS);
        }
        return value;
    }

    private static Properties classpath(String name) {
        Properties props = new Properties();
        try (InputStream in = Config.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("Не найден " + name + " (проверь -Denv / -Drelease)");
            }
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать " + name, e);
        }
        return props;
    }

    private static Properties secrets() {
        Properties props = new Properties();
        if (Files.exists(SECRETS)) {
            try (Reader reader = Files.newBufferedReader(SECRETS, StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (IOException e) {
                throw new IllegalStateException("Не удалось прочитать " + SECRETS, e);
            }
        }
        return props;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

package ru.avangard.qa.ibmobile.config;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Конфиг контура теста (dev, test, prod — задаёт BaseTest по @OnTest / @OnProd):
 * <ul>
 *   <li>env/&lt;env&gt;.properties — всё о контуре: baseUrl, release, коды подтверждения, БД;</li>
 *   <li>versions/&lt;release&gt;.properties — версии ручек релиза контура;</li>
 *   <li>~/.ibmobile/secrets.properties — логины, пароли (ключи &lt;env&gt;.*), поверх env/&lt;env&gt;.properties.</li>
 * </ul>
 */
public final class Config {

    /** Тест целиком идёт в одном потоке, поэтому контур — на поток. */
    private static final ThreadLocal<String> ENV = ThreadLocal.withInitial(() -> "dev");
    private static final Map<String, Properties> PROPS = new ConcurrentHashMap<>();
    private static final Map<String, Properties> VERSIONS = new ConcurrentHashMap<>();

    private Config() {
    }

    public static String env() {
        return ENV.get();
    }

    public static void env(String env) {
        ENV.set(env);
    }

    public static String get(String key) {
        return required(props(), key);
    }

    /** null, если не задан. */
    public static String find(String key) {
        return value(props(), key);
    }

    public static String version(String endpoint) {
        return required(VERSIONS.computeIfAbsent(get("release"), release -> resource("versions/" + release + ".properties")), endpoint);
    }

    private static Properties props() {
        return PROPS.computeIfAbsent(env(), env -> withSecrets(env, resource("env/" + env + ".properties")));
    }

    private static String required(Properties props, String key) {
        return Objects.requireNonNull(value(props, key), () -> "Не задан " + key + " (env=" + env() + ")");
    }

    /** Пустое значение = не задано: заготовки вида db.url= в test/prod. */
    private static String value(Properties props, String key) {
        String value = props.getProperty(key, "").trim();
        return value.isEmpty() ? null : value;
    }

    /** Ключ dev.MAIN.login на dev становится MAIN.login; секреты других контуров не видны. */
    private static Properties withSecrets(String env, Properties props) {
        Path path = Path.of(System.getProperty("user.home"), ".ibmobile", "secrets.properties");
        try {
            if (Files.exists(path)) {
                Properties secrets = load(Files.newBufferedReader(path, UTF_8));
                secrets.stringPropertyNames().stream()
                        .filter(key -> key.startsWith(env + "."))
                        .forEach(key -> props.setProperty(key.substring(env.length() + 1), secrets.getProperty(key)));
            }
            return props;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Properties resource(String name) {
        var in = Objects.requireNonNull(Config.class.getResourceAsStream("/" + name), "Нет " + name);
        return load(new InputStreamReader(in, UTF_8));
    }

    private static Properties load(Reader reader) {
        try (reader) {
            Properties props = new Properties();
            props.load(reader);
            return props;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

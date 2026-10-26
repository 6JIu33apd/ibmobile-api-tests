package ru.avangard.qa.ibmobile.config;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Конфиг прогона. Контур — -Denv, релиз бэка — -Drelease; параметры:
 * <ul>
 *   <li>env/&lt;env&gt;.properties — всё о контуре: baseUrl, потоки, коды подтверждения, БД;</li>
 *   <li>versions/&lt;release&gt;.properties — версии ручек релиза;</li>
 *   <li>~/.ibmobile/secrets.properties — логины, пароли (ключи &lt;env&gt;.*), поверх env/&lt;env&gt;.properties.</li>
 * </ul>
 */
public final class Config {

    /** dev | test | prod. */
    public static final String ENV = System.getProperty("env", "dev");
    /** Текущий релиз бэка; новый релиз — поменять здесь и добавить versions/&lt;релиз&gt;.properties. */
    public static final String RELEASE = System.getProperty("release", "3.20");

    private static final Properties PROPS = withSecrets(resource("env/" + ENV + ".properties"));
    private static final Properties VERSIONS = resource("versions/" + RELEASE + ".properties");

    private Config() {
    }

    public static String get(String key) {
        return required(PROPS, key);
    }

    /** null, если не задан. */
    public static String find(String key) {
        return value(PROPS, key);
    }

    public static String version(String endpoint) {
        return required(VERSIONS, endpoint);
    }

    private static String required(Properties props, String key) {
        return Objects.requireNonNull(value(props, key), () -> "Не задан " + key + " (env=" + ENV + ", release=" + RELEASE + ")");
    }

    /** Пустое значение = не задано: заготовки вида db.url= в test/prod. */
    private static String value(Properties props, String key) {
        String value = props.getProperty(key, "").trim();
        return value.isEmpty() ? null : value;
    }

    /** Ключ dev.MAIN.login на dev становится MAIN.login; секреты других контуров не видны. */
    private static Properties withSecrets(Properties props) {
        Path path = Path.of(System.getProperty("user.home"), ".ibmobile", "secrets.properties");
        try {
            if (Files.exists(path)) {
                Properties secrets = load(Files.newBufferedReader(path, UTF_8));
                secrets.stringPropertyNames().stream()
                        .filter(key -> key.startsWith(ENV + "."))
                        .forEach(key -> props.setProperty(key.substring(ENV.length() + 1), secrets.getProperty(key)));
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

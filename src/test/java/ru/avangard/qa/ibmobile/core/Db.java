package ru.avangard.qa.ibmobile.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Чтение из Oracle контура. URL — db.url в env/&lt;env&gt;.properties,
 * учётка — &lt;env&gt;.db.user / &lt;env&gt;.db.password в ~/.ibmobile/secrets.properties.
 */
public final class Db {

    private Db() {
    }

    /** Последний SMS-код пользователя (SQL — db.smsCodeSql в env/&lt;env&gt;.properties). */
    public static String smsCode(String login) {
        return queryOne(Config.get("db.smsCodeSql"), login);
    }

    /** Первая колонка первой строки результата. */
    public static String queryOne(String sql, Object... params) {
        try (Connection connection = DriverManager.getConnection(
                Config.get("db.url"), Config.secret("db.user"), Config.secret("db.password"))) {
            connection.setReadOnly(true);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    statement.setObject(i + 1, params[i]);
                }
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Пустой результат: " + sql);
                    }
                    return rs.getString(1);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Ошибка запроса к БД: " + sql, e);
        }
    }
}

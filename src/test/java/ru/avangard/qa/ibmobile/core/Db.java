package ru.avangard.qa.ibmobile.core;

import ru.avangard.qa.ibmobile.config.Config;

import java.sql.DriverManager;
import java.sql.SQLException;

/** Oracle контура: db.url, db.user, db.password. */
public final class Db {

    private Db() {
    }

    /** На test и prod код SMS хранится в БД открытым текстом. */
    public static String smsCode(String login) {
        return queryOne(Config.get("db.smsCodeSql"), login);
    }

    /** Первая колонка первой строки. */
    private static String queryOne(String sql, Object... params) {
        try (var connection = DriverManager.getConnection(Config.get("db.url"), Config.get("db.user"), Config.get("db.password"));
             var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Пусто: " + sql);
                }
                return resultSet.getString(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }
}

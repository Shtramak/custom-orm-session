package ua.shtramak.customormsession.exception;

import java.sql.SQLException;

public class SessionSqlException extends RuntimeException {

    public SessionSqlException(String message) {
        super(message);
    }

    public SessionSqlException(String message, Exception cause) {
        super(message, cause);
    }
}

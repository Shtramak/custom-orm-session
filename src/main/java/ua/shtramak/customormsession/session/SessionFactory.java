package ua.shtramak.customormsession.session;

import lombok.Getter;

import javax.sql.DataSource;

public class SessionFactory {
    @Getter
    private final DataSource dataSource;

    private SessionFactory(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static SessionFactory from(DataSource dataSource) {
        return new SessionFactory(dataSource);
    }

    public Session createSession() {
        return new SessionImpl(this);
    }
}

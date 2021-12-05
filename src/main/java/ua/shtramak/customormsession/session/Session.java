package ua.shtramak.customormsession.session;

public interface Session extends AutoCloseable {
    <T> T find(Class<T> entityType, Object id);
}

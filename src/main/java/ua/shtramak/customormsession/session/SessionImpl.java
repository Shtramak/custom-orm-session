package ua.shtramak.customormsession.session;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import ua.shtramak.customormsession.annotation.Column;
import ua.shtramak.customormsession.annotation.Table;
import ua.shtramak.customormsession.exception.SessionSqlException;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SessionImpl implements Session {
    private static final String SELECT_SQL = "SELECT * FROM %s WHERE id=%s";
    private final DataSource dataSource;
    private final Map<ObjectId, Object> persistenceCache = new ConcurrentHashMap<>();
    private final Map<ObjectId, Object> snapShotCache = new HashMap<>();

    public SessionImpl(SessionFactory sessionFactory) {
        this.dataSource = sessionFactory.getDataSource();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T find(Class<T> entityType, Object id) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(entityType);
        var objectId = new ObjectId(id, entityType.getName());
        return (T) persistenceCache.computeIfAbsent(objectId, key -> entityFromDb(entityType, key));
    }

    public <T> T entityFromDb(Class<T> entityType, ObjectId objId) {
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.createStatement()) {
                var selectQuery = String.format(SELECT_SQL, tableNameFrom(entityType), objId.id);
                var resultSet = statement.executeQuery(selectQuery);
                var entityFromDb = entityClassFrom(resultSet, entityType);
                if (entityFromDb != null) {
                    putToSnapshotCache(entityFromDb, objId);
                }
                return entityFromDb;
            }
        } catch (SQLException exception) {
            throw new SessionSqlException("Something goes wrong... Reason: " + exception.getMessage(), exception);
        }
    }

    @SneakyThrows
    private <T> T entityClassFrom(ResultSet resultSet, Class<T> entityType) {
        if (resultSet.next()) {
            var declaredFields = entityType.getDeclaredFields();
            T resultEntity = entityType.getConstructor().newInstance();
            for (Field declaredField : declaredFields) {
                declaredField.setAccessible(true);
                String columnName = columnNameFrom(declaredField);
                var columnValue = resultSet.getObject(columnName);
                declaredField.set(resultEntity, columnValue);
            }
            return resultEntity;
        }
        return null;
    }

    @SneakyThrows
    private <T> void putToSnapshotCache(T entityFromDb, ObjectId key) {
        Class<?> entityType = entityFromDb.getClass();
        var snapshot = entityType.getConstructor().newInstance();
        var declaredFields = entityType.getDeclaredFields();
        for (Field declaredField : declaredFields) {
            declaredField.setAccessible(true);
            declaredField.set(snapshot, declaredField.get(entityFromDb));
        }
        snapShotCache.put(key, snapshot);
    }

    private String columnNameFrom(Field declaredField) {
        if (declaredField.isAnnotationPresent(Column.class)) {
            return declaredField.getAnnotation(Column.class).name();
        }
        return declaredField.getName();
    }

    private <T> String tableNameFrom(Class<T> entityType) {
        if (entityType.isAnnotationPresent(Table.class)) {
            return entityType.getAnnotation(Table.class).name();
        }
        throw new SessionSqlException("Entity class must be marked with Table annotation");
    }

    @Override
    public void close() {
        persistenceCache.keySet().stream()
                .filter(this::isUpdateRequiredForKey)
                .forEach(this::performUpdate);
        persistenceCache.clear();
        snapShotCache.clear();
    }

    private void performUpdate(ObjectId key) {
        var objectForUpdate = persistenceCache.get(key);
        var updateQueryBuilder = new StringBuilder("UPDATE ");
        updateQueryBuilder.append(tableNameFrom(objectForUpdate.getClass())).append(" SET");
        var columnsWithValues = Arrays.stream(objectForUpdate.getClass().getDeclaredFields())
                .filter(Predicate.not(field -> field.getName().equals("id")))
                .map(field -> fieldWithValueSqlFormat(objectForUpdate, field))
                .collect(Collectors.joining(","));
        updateQueryBuilder.append(" ").append(columnsWithValues).append(" WHERE id=").append(key.id);
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate(updateQueryBuilder.toString());
            }
        } catch (Exception exception) {
            throw new SessionSqlException("Something goes wrong... Reason: " + exception.getMessage(), exception);
        }
    }

    @SneakyThrows
    private String fieldWithValueSqlFormat(Object objectForUpdate, Field field) {
        field.setAccessible(true);
        return field.getName() + "=" + "'" + field.get(objectForUpdate) + "'";
    }

    @SneakyThrows
    private boolean isUpdateRequiredForKey(ObjectId key) {
        var snapshot = snapShotCache.get(key);
        var original = persistenceCache.get(key);
        for (Field declaredField : original.getClass().getDeclaredFields()) {
            declaredField.setAccessible(true);
            var snapshotFieldValue = declaredField.get(snapshot);
            var originalFieldValue = declaredField.get(original);
            if (!Objects.equals(snapshotFieldValue, originalFieldValue)) {
                return true;
            }
        }
        return false;
    }

    @EqualsAndHashCode
    @AllArgsConstructor
    private static class ObjectId {
        private Object id;
        private String className;
    }
}

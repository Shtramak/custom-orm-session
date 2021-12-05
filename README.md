## Hibernate Essentials. Persistence Context. Session.

1. Create a custom _**SessionFactory**_ class that accepts DataSource and has a _**createSession**_ method
2. Create a custom _**Session**_ class that accepts _**DataSource**_ and has methods _**find(Class<T> entityType, Object id)**_ and _**close()**_
3. Create custom annotations _**Table**_, _**Column**_
4. Implement method find using _**JDBC API**_
5. Introduce session cache
   - Store loaded entities to the map when it’s loaded
   - Don’t call the DB if entity with given id is already loaded, return value from the map instead
6. Introduce update mechanism
   - Create another map that will store an entity snapshot copy (initial field values)
   - On session close, compare entity with its copy and if at least one field has changed, perform UPDATE statement

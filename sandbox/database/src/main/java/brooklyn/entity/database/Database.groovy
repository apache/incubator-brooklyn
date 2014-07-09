package brooklyn.entity.database

import brooklyn.event.basic.BasicConfigKey

/**
 * Intended to represent a SQL relational database service.
 *
 * TODO work in progress
 */
public interface Database {
    BasicConfigKey<String> SQL_VERSION = [ String, "database.sql.version", "SQL version" ]

    Collection<Schema> getSchemas();

    void createSchema(String name, Map properties);

    void addSchema(Schema schema);

    void removeSchema(String schemaName);
}

/**
 * Intended to represent a SQL database schema.
 *
 * TODO work in progress
 */
public interface Schema {
    BasicConfigKey<String> SCHEMA_NAME = [ String, "database.schema", "Database schema name" ]

    void create();
    
    void remove();
    
    String getName();
}

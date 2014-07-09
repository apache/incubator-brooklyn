/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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

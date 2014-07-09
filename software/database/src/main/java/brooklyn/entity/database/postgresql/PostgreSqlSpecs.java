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
package brooklyn.entity.database.postgresql;

import brooklyn.entity.chef.ChefConfig;
import brooklyn.entity.chef.ChefConfig.ChefModes;
import brooklyn.entity.proxying.EntitySpec;

/**
 * Utiltiy for creating specs for {@link PostgreSqlNode} instances.
 */
public class PostgreSqlSpecs {

    private PostgreSqlSpecs() {}

    public static EntitySpec<PostgreSqlNode> spec() {
        return EntitySpec.create(PostgreSqlNode.class);
    }

    /** Requires {@code knife}. */
    public static EntitySpec<PostgreSqlNode> specChef() {
        EntitySpec<PostgreSqlNode> spec = EntitySpec.create(PostgreSqlNode.class, PostgreSqlNodeChefImplFromScratch.class);
        spec.configure(ChefConfig.CHEF_MODE, ChefModes.KNIFE);
        return spec;
    }
}

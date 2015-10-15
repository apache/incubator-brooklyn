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
package org.apache.brooklyn.entity.database.mysql;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.effector.Effectors;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.entity.database.DatastoreMixins.CanExecuteScript;
import org.apache.brooklyn.util.core.task.DynamicTasks;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;

public class MySqlClusterUtils {
    protected static final Predicate<Entity> IS_MASTER = EntityPredicates.configEqualTo(MySqlNode.MYSQL_SERVER_ID, MySqlClusterImpl.MASTER_SERVER_ID);

    protected static String executeSqlOnNode(MySqlNode node, String commands) {
        return executeSqlOnNodeAsync(node, commands).getUnchecked();
    }

    // Can't call node.executeScript directly, need to change execution context, so use an effector task
    protected static Task<String> executeSqlOnNodeAsync(MySqlNode node, String commands) {
        return DynamicTasks.queue(Effectors.invocation(node, MySqlNode.EXECUTE_SCRIPT, ImmutableMap.of(CanExecuteScript.COMMANDS.getName(), commands))).asTask();
    }

    protected static String validateSqlParam(String config) {
        // Don't go into escape madness, just deny any suspicious strings.
        // Would be nice to use prepared statements, but not worth pulling in the extra dependencies.
        if (config.contains("'") && config.contains("\\")) {
            throw new IllegalStateException("User provided string contains illegal SQL characters: " + config);
        }
        return config;
    }

}

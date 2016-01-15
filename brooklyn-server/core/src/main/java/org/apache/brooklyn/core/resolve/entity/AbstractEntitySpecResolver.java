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
package org.apache.brooklyn.core.resolve.entity;

import java.util.Set;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.util.text.Strings;

public abstract class AbstractEntitySpecResolver implements EntitySpecResolver {
    private static final String PREFIX_DELIMITER = ":";
    protected final String name;
    protected final String prefix;
    protected ManagementContext mgmt;

    public AbstractEntitySpecResolver(String name) {
        this.name = name;
        this.prefix = name + PREFIX_DELIMITER;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean accepts(String type, BrooklynClassLoadingContext loader) {
        return type.startsWith(prefix) && canResolve(type, loader);
    }

    protected boolean canResolve(String type, BrooklynClassLoadingContext loader) {
        return true;
    }

    protected String getLocalType(String type) {
        return Strings.removeFromStart(type, prefix).trim();
    }

    @Override
    public void setManagementContext(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }

    @Override
    public abstract EntitySpec<?> resolve(String type, BrooklynClassLoadingContext loader, Set<String> encounteredTypes);

}

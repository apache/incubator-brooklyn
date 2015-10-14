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
package org.apache.brooklyn.camp.brooklyn.spi.creation.service;

import java.util.Set;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.entity.chef.ChefConfig;
import org.apache.brooklyn.entity.chef.ChefEntity;

public class ChefServiceSpecResolver extends AbstractServiceSpecResolver {
    private static final String RESOLVER_NAME = "chef";

    public ChefServiceSpecResolver() {
        super(RESOLVER_NAME);
    }

    @Override
    public EntitySpec<?> resolve(String type, BrooklynClassLoadingContext loader, Set<String> encounteredTypes) {
        return EntitySpec.create(ChefEntity.class)
                .configure(ChefConfig.CHEF_COOKBOOK_PRIMARY_NAME, getLocalType(type));
    }

}

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
package org.apache.brooklyn.core.config.external;

import org.apache.brooklyn.api.mgmt.ManagementContext;


/**
 * Default superclass for all {@link ExternalConfigSupplier} implementations.
 */
abstract public class AbstractExternalConfigSupplier implements ExternalConfigSupplier {

    private final ManagementContext managementContext;
    private final String name;

    protected AbstractExternalConfigSupplier(ManagementContext managementContext, String name) {
        this.managementContext = managementContext;
        this.name = name;
    }

    public ManagementContext getManagementContext() {
        return managementContext;
    }

    public String getName() {
        return name;
    }

}

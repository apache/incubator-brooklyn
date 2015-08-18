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
package org.apache.brooklyn.entity.brooklynnode;

import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.util.text.Strings;

public class LocalBrooklynNodeImpl extends BrooklynNodeImpl implements LocalBrooklynNode {

    private static final String LOCAL_BROOKLYN_NODE_KEY = "brooklyn.entity.brooklynnode.local.%s";
    private static final String BROOKLYN_WEBCONSOLE_PASSWORD_KEY = "brooklyn.webconsole.security.user.%s.password";

    @Override
    protected void connectSensors() {
        // Override management username and password from brooklyn.properties
        BrooklynProperties properties = (BrooklynProperties) getManagementContext().getConfig();
        String user = (String) properties.get(String.format(LOCAL_BROOKLYN_NODE_KEY, "user"));
        String password = (String) properties.get(String.format(LOCAL_BROOKLYN_NODE_KEY, "password"));
        if (Strings.isBlank(password)) {
            if (Strings.isBlank(user)) user = "admin";
            password = (String) properties.get(String.format(BROOKLYN_WEBCONSOLE_PASSWORD_KEY, user));
        }
        if (Strings.isNonBlank(user) && Strings.isNonBlank(password)) {
            setConfig(MANAGEMENT_USER, user);
            setConfig(MANAGEMENT_PASSWORD, password);
        }
        super.connectSensors();
    }

}

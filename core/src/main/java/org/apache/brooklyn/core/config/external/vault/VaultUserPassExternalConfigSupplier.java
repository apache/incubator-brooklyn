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
package org.apache.brooklyn.core.config.external.vault;

import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.util.text.Strings;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;

public class VaultUserPassExternalConfigSupplier extends VaultExternalConfigSupplier {
    public VaultUserPassExternalConfigSupplier(ManagementContext managementContext, String name, Map<String, String> config) {
        super(managementContext, name, config);
    }

    @Override
    protected String initAndLogIn(Map<String, String> config) {
        List<String> errors = Lists.newArrayListWithCapacity(2);
        String username = config.get("username");
        if (Strings.isBlank(username)) errors.add("missing configuration 'username'");
        String password = config.get("password");
        if (Strings.isBlank(username)) errors.add("missing configuration 'password'");
        if (!errors.isEmpty()) {
            String message = String.format("Problem configuration Vault external config supplier '%s': %s",
                    name, Joiner.on(System.lineSeparator()).join(errors));
            throw new IllegalArgumentException(message);
        }

        String path = "v1/auth/userpass/login/" + username;
        ImmutableMap<String, String> requestData = ImmutableMap.of("password", password);
        ImmutableMap<String, String> headers = MINIMAL_HEADERS;
        JsonObject response = apiPost(path, headers, requestData);
        return response.getAsJsonObject("auth").get("client_token").getAsString();
    }
}

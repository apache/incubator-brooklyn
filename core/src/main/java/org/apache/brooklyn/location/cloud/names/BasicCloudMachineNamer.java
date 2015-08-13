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
package org.apache.brooklyn.location.cloud.names;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;

import org.apache.brooklyn.location.cloud.CloudLocationConfig;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.StringShortener;
import brooklyn.util.text.Strings;

/** 
 * Standard implementation of {@link CloudMachineNamer},
 * which looks at several of the properties of the context (entity)
 * and is clever about abbreviating them. */
public class BasicCloudMachineNamer extends AbstractCloudMachineNamer {

    @Override
    protected String generateNewIdOfLength(ConfigBag setup, int len) {
        Object context = setup.peek(CloudLocationConfig.CALLER_CONTEXT);
        Entity entity = null;
        if (context instanceof Entity) entity = (Entity) context;
        
        StringShortener shortener = Strings.shortener().separator("-");
        shortener.append("system", "brooklyn");
        
        // randId often not necessary, as an 8-char hex identifier is added later (in jclouds? can we override?)
        // however it can be useful to have this early in the string, to prevent collisions in places where it is abbreviated 
        shortener.append("randId", Identifiers.makeRandomId(4));
        
        String user = System.getProperty("user.name");
        if (!"brooklyn".equals(user))
            // include user; unless the user is 'brooklyn', as 'brooklyn-brooklyn-' is just silly!
            shortener.append("user", user);
        
        if (entity!=null) {
            Application app = entity.getApplication();
            if (app!=null) {
                shortener.append("app", shortName(app))
                        .append("appId", app.getId());
            }
            shortener.append("entity", shortName(entity))
                    .append("entityId", entity.getId());
        } else if (context!=null) {
            shortener.append("context", context.toString());
        }
        
        shortener.truncate("user", 12)
                .truncate("app", 16)
                .truncate("entity", 16)
                .truncate("appId", 4)
                .truncate("entityId", 4)
                .truncate("context", 12);
        
        shortener.canTruncate("user", 8)
                .canTruncate("app", 5)
                .canTruncate("entity", 5)
                .canTruncate("system", 2)
                .canTruncate("app", 3)
                .canTruncate("entity", 3)
                .canRemove("app")
                .canTruncate("user", 4)
                .canRemove("entity")
                .canTruncate("context", 4)
                .canTruncate("randId", 2)
                .canRemove("user")
                .canTruncate("appId", 2)
                .canRemove("appId");
        
        String s = shortener.getStringOfMaxLength(len);
        return sanitize(s).toLowerCase();
    }
    
}

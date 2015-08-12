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
package brooklyn.management.entitlement;

import java.util.Map;

import org.apache.brooklyn.management.entitlement.EntitlementClass;
import org.apache.brooklyn.management.entitlement.EntitlementContext;
import org.apache.brooklyn.management.entitlement.EntitlementManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigPredicates;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.Strings;

import com.google.common.base.Preconditions;

public class PerUserEntitlementManager implements EntitlementManager {

    private static final Logger log = LoggerFactory.getLogger(PerUserEntitlementManager.class);
    
    public final static String PER_USER_ENTITLEMENTS_CONFIG_PREFIX = Entitlements.ENTITLEMENTS_CONFIG_PREFIX+".perUser";
    
    public final static ConfigKey<String> DEFAULT_MANAGER = ConfigKeys.newStringConfigKey(PER_USER_ENTITLEMENTS_CONFIG_PREFIX+
        ".default", "Default entitlements manager for users without further specification", "minimal");
    
    protected final EntitlementManager defaultManager;
    protected final Map<String,EntitlementManager> perUserManagers = MutableMap.of();

    private final static ThreadLocal<Boolean> ACTIVE = new ThreadLocal<Boolean>();
    
    private static EntitlementManager load(BrooklynProperties properties, String type) {
        if (Boolean.TRUE.equals(ACTIVE.get())) {
            // prevent infinite loop
            throw new IllegalStateException("Cannot set "+PerUserEntitlementManager.class.getName()+" within config for itself");
        }
        try {
            ACTIVE.set(true);
            return Entitlements.load(null, properties, type);
        } finally {
            ACTIVE.remove();
        }
    }
    
    public PerUserEntitlementManager(BrooklynProperties properties) {
        this(load(properties, properties.getConfig(DEFAULT_MANAGER)));
        
        BrooklynProperties users = properties.submap(ConfigPredicates.startingWith(PER_USER_ENTITLEMENTS_CONFIG_PREFIX+"."));
        for (Map.Entry<ConfigKey<?>,?> key: users.getAllConfig().entrySet()) {
            if (key.getKey().getName().equals(DEFAULT_MANAGER.getName())) continue;
            String user = Strings.removeFromStart(key.getKey().getName(), PER_USER_ENTITLEMENTS_CONFIG_PREFIX+".");
            addUser(user, load(properties, Strings.toString(key.getValue())));
        }
        
        log.info(getClass().getSimpleName()+" created with "+perUserManagers.size()+" user"+Strings.s(perUserManagers)+" and "
            + "default "+defaultManager+" (users: "+perUserManagers+")");
    }
    
    public PerUserEntitlementManager(EntitlementManager defaultManager) {
        this.defaultManager = Preconditions.checkNotNull(defaultManager);
    }

    public void addUser(String user, EntitlementManager managerForThisUser) {
        perUserManagers.put(Preconditions.checkNotNull(user, "user"), Preconditions.checkNotNull(managerForThisUser, "managerForThisUser"));
    }

    @Override
    public <T> boolean isEntitled(EntitlementContext context, EntitlementClass<T> entitlementClass, T entitlementClassArgument) {
        EntitlementManager entitlementInEffect = null;
        if (context==null || context.user()==null) {
            // no user means it is running as an internal process, always has root
            entitlementInEffect = Entitlements.root(); 
        } else {
            if (context!=null) entitlementInEffect = perUserManagers.get(context.user());
            if (entitlementInEffect==null) entitlementInEffect = defaultManager;
        }
        return entitlementInEffect.isEntitled(context, entitlementClass, entitlementClassArgument);
    }
    
}

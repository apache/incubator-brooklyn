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
package brooklyn.entity.chef.mysql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.chef.ChefConfig;
import brooklyn.entity.chef.ChefConfigs;
import brooklyn.entity.chef.ChefEntity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.util.collections.MutableMap;

/** Builds up a MySql entity via chef using specs only */
public class DynamicToyMySqlEntityChef implements ChefConfig {

    private static final Logger log = LoggerFactory.getLogger(DynamicToyMySqlEntityChef.class);

    protected static EntitySpec<? extends Entity> specBase() {
        EntitySpec<ChefEntity> spec = EntitySpec.create(ChefEntity.class);
        
        ChefConfigs.addToLaunchRunList(spec, "mysql::server");
        spec.configure(PID_FILE, "/var/run/mysqld/mysql*.pid");
        // init.d service name is sometimes mysql, sometimes mysqld, depending ubuntu/centos
        // we use pid file above instead, but this (with the right name) could be used:
//        spec.configure(SERVICE_NAME, "mysql");
        
        // chef mysql fails on first run but works on second if switching between server and solo modes
        spec.configure(ChefConfig.CHEF_RUN_CONVERGE_TWICE, true);

        // only used for solo, but safely ignored for knife
        ChefConfigs.addToCookbooksFromGithub(spec, "mysql", "build-essential", "openssl");
        // we always need dependent cookbooks set, and mysql requires password set
        // (TODO for knife we might wish to prefer things from the server)
        ChefConfigs.addLaunchAttributes(spec, MutableMap.of("mysql",  
                MutableMap.of()
                .add("server_root_password", "MyPassword")
                .add("server_debian_password", "MyPassword")
                .add("server_repl_password", "MyPassword")
            ));
        
        return spec;
    }

    public static EntitySpec<? extends Entity> spec() {
        EntitySpec<? extends Entity> spec = specBase();
        log.debug("Created entity spec for MySql: "+spec);
        return spec;
    }

    public static EntitySpec<? extends Entity> specSolo() {
        EntitySpec<? extends Entity> spec = specBase();
        spec.configure(ChefConfig.CHEF_MODE, ChefConfig.ChefModes.SOLO);
        log.debug("Created entity spec for MySql: "+spec);
        return spec;
    }

    public static EntitySpec<? extends Entity> specKnife() {
        EntitySpec<? extends Entity> spec = specBase();
        spec.configure(ChefConfig.CHEF_MODE, ChefConfig.ChefModes.KNIFE);
        log.debug("Created entity spec for MySql: "+spec);
        return spec;
    }
    
}

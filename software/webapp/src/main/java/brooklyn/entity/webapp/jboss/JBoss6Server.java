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
package brooklyn.entity.webapp.jboss;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

@Catalog(name="JBoss Application Server 6", description="AS6: an open source Java application server from JBoss", iconUrl="classpath:///jboss-logo.png")
@ImplementedBy(JBoss6ServerImpl.class)
public interface JBoss6Server extends JavaWebAppSoftwareProcess, UsesJmx {

    // TODO Instead of using portIncrement, would prefer to use http_port as "8080+" etc.
    // On localhost, if an existing jboss6 is running and consuming the required port(s), 
    // then we don't spot that and don't claim a different port.
    // Things then fail silently!
    
    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION =
            ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "6.0.0.Final");

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://downloads.sourceforge.net/project/jboss/JBoss/JBoss-${version}/jboss-as-distribution-${version}.zip?" +
            "r=http%3A%2F%2Fsourceforge.net%2Fprojects%2Fjboss%2Ffiles%2FJBoss%2F${version}%2F&ts=1307104229&use_mirror=kent");

    @SetFromFlag("bindAddress")
    BasicAttributeSensorAndConfigKey<String> BIND_ADDRESS =
            new BasicAttributeSensorAndConfigKey<String>(String.class, "jboss6.bind.address", 
                "Address of interface JBoss should listen on, defaulting 0.0.0.0 (but could set e.g. to attributeWhenReady(HOSTNAME)", 
                "0.0.0.0");

    @SetFromFlag("portIncrement")
    BasicAttributeSensorAndConfigKey<Integer> PORT_INCREMENT =
            new BasicAttributeSensorAndConfigKey<Integer>(Integer.class, "jboss6.portincrement", "Increment to be used for all jboss ports", 0);

    @SetFromFlag("clusterName")
    BasicAttributeSensorAndConfigKey<String> CLUSTER_NAME =
            new BasicAttributeSensorAndConfigKey<String>(String.class, "jboss6.clusterName", "Identifier used to group JBoss instances", "");
}

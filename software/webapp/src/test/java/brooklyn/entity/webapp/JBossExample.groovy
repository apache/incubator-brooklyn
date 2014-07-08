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
package brooklyn.entity.webapp

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.entity.webapp.jboss.JBoss7ServerImpl
import brooklyn.location.basic.LocalhostMachineProvisioningLocation

/**
 * TODO Turn into unit or integration test, or delete
 * 
 * @deprecated This should either be turned into a unit/integration test, or deleted
 */
@Deprecated
class JBossExample extends AbstractApplication {

    JBoss7Server s;
    
    @Override
    public void init() {
        s = new JBoss7ServerImpl(this, httpPort: "8080+", war:"classpath://hello-world.war");
    }

    public static void main(String[] args) {
        def ex = new JBossExample();
        ex.start( [ new LocalhostMachineProvisioningLocation(name:'london') ] )
        Entities.dumpInfo(ex)
    }
    
}

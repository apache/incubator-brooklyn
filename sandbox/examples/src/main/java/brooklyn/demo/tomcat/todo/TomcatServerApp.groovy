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
package brooklyn.demo.tomcat.todo;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation


class TomcatServerApp extends AbstractApplication {

    static BrooklynProperties sysProps = BrooklynProperties.Factory.newWithSystemAndEnvironment().addFromUrl("file:///~/brooklyn.properties");
    
    def tomcat = new TomcatServer(parent: this, httpPort: 8080, war: sysProps.getFirst("brooklyn.example.war", defaultIfNone: "/tmp/swf-booking-mvc.war"))

        
    public static void main(String... args) {
        TomcatServerApp demo = new TomcatServerApp(displayName : "tomcat server example")
        BrooklynLauncher.manage(demo)
        demo.start( [ new LocalhostMachineProvisioningLocation() ] )
    }

}

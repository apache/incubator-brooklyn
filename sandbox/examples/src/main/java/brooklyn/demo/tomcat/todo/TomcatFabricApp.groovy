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
package brooklyn.demo.tomcat.todo

import brooklyn.config.BrooklynProperties
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.group.DynamicFabric
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.location.basic.jclouds.JcloudsLocation
import brooklyn.location.basic.jclouds.JcloudsLocationFactory

/**
 * Requires the following to be set, as env var, system property, or property in file references by sys prop brooklyn.properties:
 *                  brooklyn.aws.id
 *                  brooklyn.aws.key
 *                  brooklyn.public-key-file
 *                  brooklyn.private-key-file
 *                  
 *                  brooklyn.example.war
 */
class TomcatFabricApp extends AbstractApplication {

    static BrooklynProperties sysProps = BrooklynProperties.Factory.newWithSystemAndEnvironment().addFromUrl("file:///tmp/brooklyn.properties");

	Closure webClusterFactory = { Map flags, Entity owner ->
		Map clusterFlags = flags + [newEntity: { properties -> new TomcatServer(properties) }]
		return new DynamicWebAppCluster(clusterFlags, owner)
	}

	DynamicFabric fabric = new DynamicFabric(
			owner : this,
			displayName : "WebFabric",
			displayNamePrefix : "",
			displayNameSuffix : " web cluster",
			initialSize : 2,
			newEntity : webClusterFactory,
			httpPort : 8080,  
			war: sysProps.getFirst("brooklyn.example.war", defaultIfNone: "/tmp/swf-booking-mvc.war"),           // TODO use classpath
        )
	
    public static void main(String[] argv) {
        TomcatFabricApp demo = new TomcatFabricApp(displayName : "tomcat example")
        BrooklynLauncher.manage(demo)
        
		JcloudsLocationFactory awsLocationsFactory = JcloudsLocationFactory.newAmazonWebServicesInstance(sysProps);
		JcloudsLocation locUsW1 = awsLocationsFactory.newLocation("us-west-1")
		JcloudsLocation locEuW1 = awsLocationsFactory.newLocation("eu-west-1")
        
        demo.start( [ locUsW1, locEuW1 ] )
    }

}

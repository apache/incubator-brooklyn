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

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Attributes
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.basic.jclouds.JcloudsLocation
import brooklyn.location.basic.jclouds.JcloudsLocationFactory

class TomcatClusterWithNginxApp extends AbstractApplication {
	
	NginxController nginxController = new NginxController(
		domain : "brooklyn.geopaas.org",
		port : 8000,
		portNumberSensor : Attributes.HTTP_PORT)

	ControlledDynamicWebAppCluster cluster = new ControlledDynamicWebAppCluster(
		owner : this,
		controller : nginxController,
		webServerFactory : { properties -> new TomcatServer(properties) },
		initialSize: 2,
		httpPort: 8080, war: "/path/to/booking-mvc.war")

    public static void main(String[] argv) {
        TomcatClusterWithNginxApp demo = new TomcatClusterWithNginxApp(displayName : "tomcat cluster with nginx example")
        BrooklynLauncher.manage(demo)
        
		JcloudsLocationFactory locFactory = new JcloudsLocationFactory([
					provider : "aws-ec2",
					identity : "xxxxxxxxxxxxxxxxxxxxxxxxxxx",
                    credential : "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                    sshPrivateKey : new File("/home/bob/.ssh/id_rsa.private"),
                    sshPublicKey : new File("/home/bob/.ssh/id_rsa.pub"),
					securityGroups:["my-security-group"]
				])

		JcloudsLocation loc = locFactory.newLocation("us-west-1")

        demo.start([loc])
    }
}



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

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.AbstractWebAppFixtureIntegrationTest;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.location.basic.PortRanges;
import brooklyn.test.entity.TestApplication;

public class JbossServerWebAppFixtureIntegrationTest extends AbstractWebAppFixtureIntegrationTest {

    @Test(groups = "Integration", dataProvider = "basicEntities")
    public void testReportsServiceDownWhenKilled(final SoftwareProcess entity) throws Exception {
        super.testReportsServiceDownWhenKilled(entity);
    }
    
    @DataProvider(name = "basicEntities")
    public Object[][] basicEntities() {
        TestApplication jboss6App = newTestApplication();
        JBoss6Server jboss6 = jboss6App.createAndManageChild(EntitySpec.create(JBoss6Server.class)
                .configure(JBoss6Server.PORT_INCREMENT, PORT_INCREMENT));
        
        TestApplication jboss7App = newTestApplication();
        JBoss7Server jboss7 = jboss7App.createAndManageChild(EntitySpec.create(JBoss7Server.class)
                .configure(JBoss7Server.HTTP_PORT, PortRanges.fromString(DEFAULT_HTTP_PORT)));
        
        return new JavaWebAppSoftwareProcess[][] {
                new JavaWebAppSoftwareProcess[] {jboss6}, 
                new JavaWebAppSoftwareProcess[] {jboss7}
                
        };
    }

    // to be able to test on this class in Eclipse IDE
    @Test(groups = "Integration", dataProvider = "basicEntities")
    public void canStartAndStop(final SoftwareProcess entity) {
        super.canStartAndStop(entity);
    }

//    @Override
//    // TODO override parent and add seam-booking-as{6,7}
//    @DataProvider(name = "entitiesWithWarAndURL")
//    public Object[][] entitiesWithWar() {
//        List<Object[]> result = Lists.newArrayList();
//        
//        for (Object[] entity : basicEntities()) {
//            result.add(new Object[] {
//                    entity[0],
//                    "hello-world.war",
//                    "hello-world/",
//                    "" // no sub-page path
//                    });
//        }
//        
//        TestApplication tomcatApp = newTestApplication();
//        TomcatServer tomcat = tomcatApp.createAndManageChild(EntitySpec.create(TomcatServer.class)
//                .configure(TomcatServer.HTTP_PORT, PortRanges.fromString(DEFAULT_HTTP_PORT)));
//        result.add(new Object[] {
//                tomcat,
//                "swf-booking-mvc.war",
//                "swf-booking-mvc/",
//                "spring/intro",
//               });
//            // FIXME seam-booking does not work
////            [   new JBoss6ServerImpl(parent:application, portIncrement:PORT_INCREMENT),
////              "seam-booking-as6.war",
////                "seam-booking-as6/",
////            ],
////            [   new JBoss7ServerImpl(parent:application, httpPort:DEFAULT_HTTP_PORT),
////                "seam-booking-as7.war",
////                "seam-booking-as7/",
////            ],
//        
//        return result.toArray(new Object[][] {});
//    }

    public static void main(String ...args) throws Exception {
        JbossServerWebAppFixtureIntegrationTest t = new JbossServerWebAppFixtureIntegrationTest();
        t.setUp();
        t.testReportsServiceDownWhenKilled((SoftwareProcess) t.basicEntities()[0][0]);
        t.shutdownApp();
        t.shutdownMgmt();
    }

}

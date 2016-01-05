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
package org.apache.brooklyn.entity.webapp.tomcat;

import static org.testng.Assert.assertNotNull;

import org.testng.annotations.Test;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.entity.AbstractEc2LiveTest;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.HttpTestUtils;
import org.apache.brooklyn.test.support.TestResourceUnavailableException;

import com.google.common.collect.ImmutableList;

/**
 * A simple test of installing+running on AWS-EC2, using various OS distros and versions. 
 */
public class Tomcat8ServerEc2LiveTest extends AbstractEc2LiveTest {

    public String getTestWar() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/hello-world.war");
        return "classpath://hello-world.war";
    }

    @Override
    protected void doTest(Location loc) throws Exception {
        final Tomcat8Server server = app.createAndManageChild(EntitySpec.create(Tomcat8Server.class)
                .configure("war", getTestWar()));
        
        app.start(ImmutableList.of(loc));
        
        String url = server.getAttribute(Tomcat8Server.ROOT_URL);
        
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(url, 200);
        HttpTestUtils.assertContentContainsText(url, "Hello");
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertNotNull(server.getAttribute(Tomcat8Server.REQUEST_COUNT));
                assertNotNull(server.getAttribute(Tomcat8Server.ERROR_COUNT));
                assertNotNull(server.getAttribute(Tomcat8Server.TOTAL_PROCESSING_TIME));
            }});
    }
    
    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods  
}

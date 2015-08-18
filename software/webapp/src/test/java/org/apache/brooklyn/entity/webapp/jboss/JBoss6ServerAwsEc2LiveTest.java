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
package org.apache.brooklyn.entity.webapp.jboss;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.testng.Assert.assertNotNull;

import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.HttpTestUtils;
import org.apache.brooklyn.test.TestResourceUnavailableException;
import org.testng.annotations.Test;

import brooklyn.entity.AbstractEc2LiveTest;

import com.google.common.collect.ImmutableList;

/**
 * A simple test of installing+running on AWS-EC2, using various OS distros and versions. 
 */
public class JBoss6ServerAwsEc2LiveTest extends AbstractEc2LiveTest {
    
    public String getTestWar() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/hello-world-no-mapping.war");
        return "classpath://hello-world-no-mapping.war";
    }

    @Override
    protected void doTest(Location loc) throws Exception {
        final JBoss6Server server = app.createAndManageChild(EntitySpec.create(JBoss6Server.class)
                .configure("war", getTestWar()));
        
        app.start(ImmutableList.of(loc));
        
        String url = server.getAttribute(JBoss6Server.ROOT_URL);
        
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(url, 200);
        HttpTestUtils.assertContentContainsText(url, "Hello");
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertNotNull(server.getAttribute(JBoss6Server.REQUEST_COUNT));
                assertNotNull(server.getAttribute(JBoss6Server.ERROR_COUNT));
                assertNotNull(server.getAttribute(JBoss6Server.TOTAL_PROCESSING_TIME));
                assertNotNull(server.getAttribute(JBoss6Server.MAX_PROCESSING_TIME));
                assertNotNull(server.getAttribute(JBoss6Server.BYTES_RECEIVED));
                assertNotNull(server.getAttribute(JBoss6Server.BYTES_SENT));
            }});
    }
    
    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods  
}

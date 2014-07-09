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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.testng.Assert.assertNotNull;

import java.net.URL;

import org.testng.annotations.Test;

import brooklyn.entity.AbstractGoogleComputeLiveTest;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.test.Asserts;
import brooklyn.test.HttpTestUtils;

import com.google.common.collect.ImmutableList;

/**
 * A simple test of installing+running on AWS-EC2, using various OS distros and versions. 
 */
public class Jboss7ServerGoogleComputeLiveTest extends AbstractGoogleComputeLiveTest {
    
    private URL warUrl = checkNotNull(getClass().getClassLoader().getResource("hello-world.war"));
    
    @Override
    protected void doTest(Location loc) throws Exception {
        final JBoss7Server server = app.createAndManageChild(EntitySpec.create(JBoss7Server.class)
                .configure("war", warUrl.toString()));
        
        app.start(ImmutableList.of(loc));
        
        String url = server.getAttribute(JBoss7Server.ROOT_URL);
        
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(url, 200);
        HttpTestUtils.assertContentContainsText(url, "Hello");
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertNotNull(server.getAttribute(JBoss7Server.REQUEST_COUNT));
                assertNotNull(server.getAttribute(JBoss7Server.ERROR_COUNT));
                assertNotNull(server.getAttribute(JBoss7Server.TOTAL_PROCESSING_TIME));
                assertNotNull(server.getAttribute(JBoss7Server.MAX_PROCESSING_TIME));
                assertNotNull(server.getAttribute(JBoss7Server.BYTES_RECEIVED));
                assertNotNull(server.getAttribute(JBoss7Server.BYTES_SENT));
            }});
    }
    
    @Test(groups = {"Live"})
    @Override
    public void test_DefaultImage() throws Exception {
        super.test_DefaultImage();
    }

    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods  
}

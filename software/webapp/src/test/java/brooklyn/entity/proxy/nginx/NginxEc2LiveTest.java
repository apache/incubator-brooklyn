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
package brooklyn.entity.proxy.nginx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.HttpTestUtils;

import com.google.common.collect.ImmutableList;

/**
 * A simple test of installing+running on AWS-EC2, using various OS distros and versions. 
 */
public class NginxEc2LiveTest extends AbstractEc2LiveTest {
    
    /* FIXME Currently fails on:
     *   test_Debian_5:                   installation of nginx failed
     *   test_Ubuntu_12_0:                invocation error for disable requiretty 
     */
    
    /* PASSED: test_CentOS_5_6
     * PASSED: test_CentOS_6_3
     * PASSED: test_Debian_6
     * PASSED: test_Ubuntu_10_0
     * 
     * test_Red_Hat_Enterprise_Linux_6 passes, if get it to wait for ssh-login rather than "failed to SSH in as root"
     */
    
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(NginxEc2LiveTest.class);

    private NginxController nginx;

    @Override
    protected void doTest(Location loc) throws Exception {
        nginx = app.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("portNumberSensor", WebAppService.HTTP_PORT));
        
        app.start(ImmutableList.of(loc));

        // nginx should be up, and URL reachable
        EntityTestUtils.assertAttributeEqualsEventually(nginx, SoftwareProcess.SERVICE_UP, true);
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(nginx.getAttribute(NginxController.ROOT_URL), 404);
    }
    
    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods  
}

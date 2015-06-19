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

import org.testng.annotations.Test;

import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.location.Location;

/**
 * A simple test of installing+running Wildfly 8 on AWS-EC2, using various OS distros and versions. 
 */
public class Wildfly8ServerGoogleComputeLiveTest extends JBossServerGoogleComputeLiveTest {

    @Override
    protected void doTest(Location loc) throws Exception {
    	super.doTest(loc);
    }
    
    @Test(groups = {"Live"})
    @Override
    public void test_DefaultImage() throws Exception {
        super.test_DefaultImage();
    }
    
    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods
    
    @Override
    protected Class<? extends JavaWebAppSoftwareProcess> getServerType() {
    	return Wildfly8Server.class;
    }
}

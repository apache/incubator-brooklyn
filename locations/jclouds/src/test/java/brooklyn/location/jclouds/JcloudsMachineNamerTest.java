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
package brooklyn.location.jclouds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Strings;

public class JcloudsMachineNamerTest {

    private static final Logger log = LoggerFactory.getLogger(JcloudsMachineNamerTest.class);
    
    @Test
    public void testGenerateGroupIdInVcloud() {
        ConfigBag cfg = new ConfigBag()
            .configure(JcloudsLocationConfig.CLOUD_PROVIDER, "vcloud")
            .configure(JcloudsLocationConfig.CALLER_CONTEXT, "!mycontext!");
        
        String result = new JcloudsMachineNamer(cfg).generateNewGroupId();
        
        log.info("test mycontext vcloud group id gives: "+result);
        // brooklyn-user-mycontext!-1234
        // br-user-myco-1234
        Assert.assertTrue(result.length() <= 15);
        
        String user = Strings.maxlen(System.getProperty("user.name"), 2).toLowerCase();
        // (length 2 will happen if user is brooklyn)
        Assert.assertTrue(result.indexOf(user) >= 0);
        Assert.assertTrue(result.indexOf("-myc") >= 0);
    }
    
}

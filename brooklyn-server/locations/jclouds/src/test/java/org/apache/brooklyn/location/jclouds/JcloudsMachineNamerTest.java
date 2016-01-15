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
package org.apache.brooklyn.location.jclouds;

import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

public class JcloudsMachineNamerTest {

    private static final Logger log = LoggerFactory.getLogger(JcloudsMachineNamerTest.class);
    
    @Test
    public void testGenerateGroupIdInVcloud() {
        ConfigBag cfg = new ConfigBag()
            .configure(JcloudsLocationConfig.CLOUD_PROVIDER, "vcloud")
            .configure(JcloudsLocationConfig.CALLER_CONTEXT, "!mycontext!");
        
        String result = new JcloudsMachineNamer().generateNewGroupId(cfg);
        
        log.info("test mycontext vcloud group id gives: "+result);
        // brooklyn-user-!mycontext!-1234
        // br-<code>-<user>-myco-1234
        Assert.assertTrue(result.length() <= 24-4-1, "result: "+result);
        
        String user = System.getProperty("user.name");
        String userExt = Strings.maxlen(user, 2).toLowerCase();
        
        // Username can be omitted if it is longer than the rules defined in BasicCloudMachineNamer()
        if (user.length() <= 4) { 
            // (length 2 will happen if user is brooklyn, to avoid brooklyn-brooklyn at start!)
            Assert.assertTrue(result.indexOf(userExt) >= 0);
        }
        Assert.assertTrue(result.indexOf("-myc") >= 0);
    }
    
}
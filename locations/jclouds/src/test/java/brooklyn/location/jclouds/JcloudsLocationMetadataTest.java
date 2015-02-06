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

import static org.testng.Assert.assertEquals;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;

import com.google.common.collect.ImmutableSet;

/**
 * @author Shane Witbeck
 */
public class JcloudsLocationMetadataTest implements JcloudsLocationConfig {

    protected BrooklynProperties brooklynProperties;
    protected LocalManagementContext managementContext;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = LocalManagementContextForTests.newInstance(BrooklynProperties.Factory.builderEmpty().build());
        brooklynProperties = managementContext.getBrooklynProperties();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }


    @Test
    public void testGetsDefaultAwsEc2Metadata() throws Exception {
        Location loc = managementContext.getLocationRegistry().resolve("jclouds:aws-ec2:us-west-1");
        
        assertEquals(loc.getConfig(LocationConfigKeys.LATITUDE), 40.0d);
        assertEquals(loc.getConfig(LocationConfigKeys.LONGITUDE), -120.0d);
        assertEquals(loc.getConfig(LocationConfigKeys.ISO_3166), ImmutableSet.of("US-CA"));
    }

    @Test
    public void testCanOverrideDefaultAwsEc2Metadata() throws Exception {
        brooklynProperties.put("brooklyn.location.jclouds.aws-ec2@us-west-1.latitude", "41.2");
        Location loc = managementContext.getLocationRegistry().resolve("jclouds:aws-ec2:us-west-1");
        
        assertEquals(loc.getConfig(LocationConfigKeys.LATITUDE), 41.2d);
    }
}

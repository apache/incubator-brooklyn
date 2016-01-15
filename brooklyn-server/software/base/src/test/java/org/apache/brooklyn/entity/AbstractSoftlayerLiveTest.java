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
package org.apache.brooklyn.entity;

import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.text.StringShortener;
import org.apache.brooklyn.util.text.Strings;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Runs a test with many different distros and versions.
 */
public abstract class AbstractSoftlayerLiveTest {
    
    public static final String PROVIDER = "softlayer";
    public static final int MAX_TAG_LENGTH = 20;
    public static final int MAX_VM_NAME_LENGTH = 30;

    protected BrooklynProperties brooklynProperties;
    protected ManagementContext ctx;
    
    protected TestApplication app;
    protected Location jcloudsLocation;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        List<String> propsToRemove = ImmutableList.of("imageId", "imageDescriptionRegex", "imageNameRegex", "inboundPorts", "hardwareId", "minRam");

        // Don't let any defaults from brooklyn.properties (except credentials) interfere with test
        brooklynProperties = BrooklynProperties.Factory.newDefault();
        for (String propToRemove : propsToRemove) {
            for (String propVariant : ImmutableList.of(propToRemove, CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, propToRemove))) {
                brooklynProperties.remove("brooklyn.locations.jclouds."+PROVIDER+"."+propVariant);
                brooklynProperties.remove("brooklyn.locations."+propVariant);
                brooklynProperties.remove("brooklyn.jclouds."+PROVIDER+"."+propVariant);
                brooklynProperties.remove("brooklyn.jclouds."+propVariant);
            }
        }

        // Also removes scriptHeader (e.g. if doing `. ~/.bashrc` and `. ~/.profile`, then that can cause "stdin: is not a tty")
        brooklynProperties.remove("brooklyn.ssh.config.scriptHeader");
        
        ctx = new LocalManagementContext(brooklynProperties);
        app = ApplicationBuilder.newManagedApp(TestApplication.class, ctx);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test(groups = {"Live"})
    public void test_Default() throws Exception {
        runTest(ImmutableMap.<String,Object>of());
    }

    @Test(groups = {"Live"})
    public void test_Ubuntu_12_0_4() throws Exception {
        // Image: {id=UBUNTU_12_64, providerId=UBUNTU_12_64, os={family=ubuntu, version=12.04, description=Ubuntu / Ubuntu / 12.04.0-64 Minimal, is64Bit=true}, description=UBUNTU_12_64, status=AVAILABLE, loginUser=root}
        runTest(ImmutableMap.<String,Object>of("imageId", "UBUNTU_12_64"));
    }

    @Test(groups = {"Live"})
    public void test_Centos_6_0() throws Exception {
      // Image: {id=CENTOS_6_64, providerId=CENTOS_6_64, os={family=centos, version=6.5, description=CentOS / CentOS / 6.5-64 LAMP for Bare Metal, is64Bit=true}, description=CENTOS_6_64, status=AVAILABLE, loginUser=root}
        runTest(ImmutableMap.<String,Object>of("imageId", "CENTOS_6_64"));
    }
    
    protected void runTest(Map<String,?> flags) throws Exception {
        StringShortener shortener = Strings.shortener().separator("-");
        shortener.canTruncate(getClass().getSimpleName(), MAX_TAG_LENGTH);
        Map<String,?> allFlags = MutableMap.<String,Object>builder()
                .put("tags", ImmutableList.of(shortener.getStringOfMaxLength(MAX_TAG_LENGTH)))
                .put("vmNameMaxLength", MAX_VM_NAME_LENGTH)
                .putAll(flags)
                .build();
        jcloudsLocation = ctx.getLocationRegistry().resolve(PROVIDER, allFlags);

        doTest(jcloudsLocation);
    }
    
    protected abstract void doTest(Location loc) throws Exception;
}

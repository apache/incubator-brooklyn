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
package org.apache.brooklyn.location.jclouds.provider;

import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.location.NoMachinesAvailableException;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.collections.Lists;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

public abstract class AbstractJcloudsLocationTest {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractJcloudsLocationTest.class);

    private final String provider;

    protected JcloudsLocation loc;
    protected List<SshMachineLocation> machines = MutableList.of();
    
    // NB made private in 090-SNAPSHOT so that non-live tests aren't forced to get a live context
    // (adding 10+ seconds to the build)
    //
    // use mgmt() instead to access, it is populated on demand
    private ManagementContext ctx;

    protected AbstractJcloudsLocationTest(String provider) {
        this.provider = provider;
    }

    protected synchronized ManagementContext mgmt() {
    	if (ctx==null) {
    		useMgmt(newLiveManagementContext());
    	}
    	return ctx;
    }
    
    protected synchronized void useMgmt(ManagementContext mgmt) {
    	if (ctx!=null) {
    		throw new IllegalStateException("Must shutdown old management first");
    	}
    	ctx = mgmt;
    }
    
    protected ManagementContext newLiveManagementContext() {
        BrooklynProperties props = BrooklynProperties.Factory.newDefault().addFromMap(ImmutableMap.of("provider", provider));
        return Entities.newManagementContext(props.asMapWithStringKeys());
	}

    protected ManagementContext newMockManagementContext() {
        BrooklynProperties props = BrooklynProperties.Factory.newDefault().addFromMap(ImmutableMap.of("provider", provider));
        return LocalManagementContextForTests.newInstance(props);
	}

	/**
     * The location and image id tuplets to test.
     */
    @DataProvider(name = "fromImageId")
    public abstract Object[][] cloudAndImageIds();

    /**
     * A single location and image id tuplet to test.
     */
    @DataProvider(name = "fromFirstImageId")
    public Object[][] cloudAndImageFirstId() {
        Object[][] all = cloudAndImageIds();
        return (all != null) ? new Object[][] { all[0] } : new Object[][] { };
    }

    /**
     * The location and image name pattern tuplets to test.
     */
    @DataProvider(name = "fromImageNamePattern")
    public abstract Object[][] cloudAndImageNamePatterns();

    /**
     * The location, image pattern and image owner tuplets to test.
     */
    @DataProvider(name = "fromImageDescriptionPattern")
    public abstract Object[][] cloudAndImageDescriptionPatterns();

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        List<Exception> exceptions = Lists.newArrayList();
        for (SshMachineLocation machine : machines) {
            try {
                loc.release(machine);
            } catch (Exception e) {
                LOG.warn("Error releasing {}: {}; continuing...", machine, e.getMessage());
                exceptions.add(e);
            }
        }
        if (!exceptions.isEmpty()) {
            LOG.info("Exception during tearDown: {}", Exceptions.collapseText(exceptions.get(0)));
        }
        machines.clear();
        
        if (ctx != null) {
        	Entities.destroyAllCatching(ctx);
        	ctx = null;
        }
    }

    @Test(dataProvider="fromImageId")
    public void testTagMapping(String regionName, String imageId, String imageOwner) {
    	useMgmt(newMockManagementContext());
    	
        Map<String, Object> dummy = ImmutableMap.<String, Object>of("identity", "DUMMY", "credential", "DUMMY");
        loc = (JcloudsLocation) mgmt().getLocationRegistry().resolve(provider + (regionName == null ? "" : ":" + regionName), dummy);
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.<String, Object>builder().put("imageId", imageId);
        if (imageOwner != null) builder.put("imageOwner", imageOwner);
        Map<String, Object> tagMapping = builder.build();
        loc.setTagMapping(ImmutableMap.<String, Map<String, ? extends Object>>of("MyEntityType", tagMapping));

        Map<String, Object> flags = loc.getProvisioningFlags(ImmutableList.of("MyEntityType"));
        assertTrue(Maps.<String, Object>difference(flags, tagMapping).entriesOnlyOnRight().isEmpty(), "flags="+flags);
    }

    @Test(groups = "Live", dataProvider="fromImageId")
    public void testProvisionVmUsingImageId(String regionName, String imageId, String imageOwner) {
        loc = (JcloudsLocation) mgmt().getLocationRegistry().resolve(provider + (regionName == null ? "" : ":" + regionName));
        SshMachineLocation machine = obtainMachine(MutableMap.of("imageId", imageId, "imageOwner", imageOwner, JcloudsLocation.MACHINE_CREATE_ATTEMPTS, 2));

        LOG.info("Provisioned {} vm {}; checking if ssh'able", provider, machine);
        assertTrue(machine.isSshable());
    }
    
    @Test(groups = "Live", dataProvider="fromImageNamePattern")
    public void testProvisionVmUsingImageNamePattern(String regionName, String imageNamePattern, String imageOwner) {
        loc = (JcloudsLocation) mgmt().getLocationRegistry().resolve(provider + (regionName == null ? "" : ":" + regionName));
        SshMachineLocation machine = obtainMachine(MutableMap.of("imageNameRegex", imageNamePattern, "imageOwner", imageOwner, JcloudsLocation.MACHINE_CREATE_ATTEMPTS, 2));
        
        LOG.info("Provisioned {} vm {}; checking if ssh'able", provider, machine);
        assertTrue(machine.isSshable());
    }
    
    @Test(groups = "Live", dataProvider="fromImageDescriptionPattern")
    public void testProvisionVmUsingImageDescriptionPattern(String regionName, String imageDescriptionPattern, String imageOwner) {
        loc = (JcloudsLocation) mgmt().getLocationRegistry().resolve(provider + (regionName == null ? "" : ":" + regionName));
        SshMachineLocation machine = obtainMachine(MutableMap.of("imageDescriptionRegex", imageDescriptionPattern, "imageOwner", imageOwner, JcloudsLocation.MACHINE_CREATE_ATTEMPTS, 2));
        
        LOG.info("Provisioned {} vm {}; checking if ssh'able", provider, machine);
        assertTrue(machine.isSshable());
    }

    // Use this utility method to ensure machines are released on tearDown
    protected SshMachineLocation obtainMachine(Map<?,?> flags) {
        try {
            SshMachineLocation result = (SshMachineLocation)loc.obtain(flags);
            machines.add(result);
            return result;
        } catch (NoMachinesAvailableException nmae) {
            LOG.warn("No machines available", nmae);
            throw Exceptions.propagate(nmae);
        }
    }
    
    protected SshMachineLocation release(SshMachineLocation machine) {
        machines.remove(machine);
        loc.release(machine);
        return machine;
    }
}

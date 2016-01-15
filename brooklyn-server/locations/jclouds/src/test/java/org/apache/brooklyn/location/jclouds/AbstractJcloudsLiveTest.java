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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.List;
import java.util.Map;

import org.apache.brooklyn.util.exceptions.CompoundRuntimeException;
import org.apache.brooklyn.api.location.MachineLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.location.ssh.SshMachineLocation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

public class AbstractJcloudsLiveTest {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractJcloudsLiveTest.class);

    public static final String BROOKLYN_PROPERTIES_PREFIX = "brooklyn.location.jclouds.";
    public static final String BROOKLYN_PROPERTIES_LEGACY_PREFIX = "brooklyn.jclouds.";
    
    public static final String AWS_EC2_PROVIDER = "aws-ec2";
    public static final String AWS_EC2_MICRO_HARDWARE_ID = "t1.micro";
    public static final String AWS_EC2_SMALL_HARDWARE_ID = "m1.small";
    public static final String AWS_EC2_MEDIUM_HARDWARE_ID = "m3.medium";
    public static final String AWS_EC2_EUWEST_REGION_NAME = "eu-west-1";
    public static final String AWS_EC2_USEAST_REGION_NAME = "us-east-1";

    public static final String RACKSPACE_PROVIDER = "rackspace-cloudservers-uk";
    
    public static final String SOFTLAYER_PROVIDER = "softlayer";
    public static final String SOFTLAYER_AMS01_REGION_NAME = "ams01";
    
    public static final String GCE_PROVIDER = "google-compute-engine";
    public static final String GCE_USCENTRAL_REGION_NAME = "us-central1-a";
    
    protected BrooklynProperties brooklynProperties;
    protected LocalManagementContext managementContext;
    
    protected List<JcloudsSshMachineLocation> machines;
    protected JcloudsLocation jcloudsLocation;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        machines = Lists.newCopyOnWriteArrayList();
        managementContext = newManagementContext();
        
        // Don't let any defaults from brooklyn.properties (except credentials) interfere with test
        brooklynProperties = managementContext.getBrooklynProperties();
        stripBrooklynProperties(brooklynProperties);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        List<Exception> exceptions = Lists.newArrayList();
        try {
            if (machines != null) {
                exceptions.addAll(releaseMachineSafely(machines));
                machines.clear();
            }
        } finally {
            try {
                if (managementContext != null) Entities.destroyAll(managementContext);
            } catch (Exception e) {
                LOG.warn("Error destroying management context", e);
                exceptions.add(e);
            }
        }
        
        // TODO Debate about whether to:
        //  - use destroyAllCatching (i.e. not propagating exception)
        //    Benefit is that other tests in class will subsequently be run, rather than bailing out.
        //  - propagate exceptions from tearDown
        //    Benefit is that we don't hide errors; release(...) etc should not be throwing exceptions.
        if (exceptions.size() > 0) {
            throw new CompoundRuntimeException("Error in tearDown of "+getClass(), exceptions);
        }
    }

    protected LocalManagementContext newManagementContext() {
        // loads properties, by default, but not OSGi or anything else
        return LocalManagementContextForTests.builder(true).useDefaultProperties().build();
    }
    
    protected static void stripBrooklynProperties(BrooklynProperties props) {
        // remove all location properties except for identity and credential
        // (so key, scripts, etc settings don't interfere with tests) 
        for (String key : ImmutableSet.copyOf(props.asMapWithStringKeys().keySet())) {
            if (key.startsWith(BROOKLYN_PROPERTIES_PREFIX) && !(key.endsWith("identity") || key.endsWith("credential"))) {
                props.remove(key);
            }
            if (key.startsWith(BROOKLYN_PROPERTIES_LEGACY_PREFIX) && !(key.endsWith("identity") || key.endsWith("credential"))) {
                props.remove(key);
            }
            
            // Also removes scriptHeader (e.g. if doing `. ~/.bashrc` and `. ~/.profile`, then that can cause "stdin: is not a tty")
            if (key.startsWith("brooklyn.ssh")) {
                props.remove(key);
            }
        }
    }
    
    protected void assertSshable(SshMachineLocation machine) {
        int result = machine.execScript("simplecommand", ImmutableList.of("true"));
        assertEquals(result, 0);
    }

    // Use this utility method to ensure machines are released on tearDown
    protected JcloudsSshMachineLocation obtainMachine(Map<?, ?> conf) throws Exception {
        assertNotNull(jcloudsLocation);
        JcloudsSshMachineLocation result = (JcloudsSshMachineLocation)jcloudsLocation.obtain(conf);
        machines.add(checkNotNull(result, "result"));
        return result;
    }

    protected JcloudsSshMachineLocation obtainMachine() throws Exception {
        return obtainMachine(ImmutableMap.of());
    }
    
    protected void releaseMachine(JcloudsSshMachineLocation machine) {
        assertNotNull(jcloudsLocation);
        machines.remove(machine);
        jcloudsLocation.release(machine);
    }
    
    protected List<Exception> releaseMachineSafely(Iterable<? extends JcloudsSshMachineLocation> machines) {
        List<Exception> exceptions = Lists.newArrayList();
        
        for (JcloudsSshMachineLocation machine : machines) {
            try {
                releaseMachine(machine);
            } catch (Exception e) {
                LOG.warn("Error releasing machine "+machine+"; continuing...", e);
                exceptions.add(e);
            }
        }
        return exceptions;
    }

    protected void suspendMachine(MachineLocation machine) {
        assertNotNull(jcloudsLocation);
        machines.remove(machine);
        jcloudsLocation.suspendMachine(machine);
    }

    protected MachineLocation resumeMachine(Map<?, ?> flags) {
        assertNotNull(jcloudsLocation);
        MachineLocation location = jcloudsLocation.resumeMachine(flags);
        machines.add((JcloudsSshMachineLocation) location);
        return location;
    }

}

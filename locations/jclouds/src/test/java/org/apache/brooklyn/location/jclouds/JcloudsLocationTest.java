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

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.location.MachineLocationCustomizer;
import org.apache.brooklyn.api.location.NoMachinesAvailableException;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.location.LocationConfigKeys;
import org.apache.brooklyn.core.location.cloud.names.CustomMachineNamer;
import org.apache.brooklyn.core.location.geo.HostGeoInfo;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.jclouds.scriptbuilder.domain.OsFamily;
import org.jclouds.scriptbuilder.domain.StatementList;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.location.jclouds.JcloudsLocation.UserCreation;

/**
 * @author Shane Witbeck
 */
public class JcloudsLocationTest implements JcloudsLocationConfig {

    private static final Logger log = LoggerFactory.getLogger(JcloudsLocationTest.class);
    
    public static Predicate<ConfigBag> checkerFor(final String user, final Integer minRam, final Integer minCores) {
        return new Predicate<ConfigBag>() {
            @Override
            public boolean apply(@Nullable ConfigBag input) {
                Assert.assertEquals(input.get(USER), user);
                Assert.assertEquals(input.get(MIN_RAM), minRam);
                Assert.assertEquals(input.get(MIN_CORES), minCores);
                return true;
            }
        };
    }
    
    public static Predicate<ConfigBag> templateCheckerFor(final String ports) {
        return new Predicate<ConfigBag>() {
            @Override
            public boolean apply(@Nullable ConfigBag input) {
                Assert.assertEquals(input.get(INBOUND_PORTS), ports);
                return false;
            }
        };
    }
    
    private LocalManagementContext managementContext;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = LocalManagementContextForTests.newInstance(BrooklynProperties.Factory.builderEmpty().build());
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearUp() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testCreateWithFlagsDirectly() throws Exception {
        BailOutJcloudsLocation jcl = BailOutJcloudsLocation.newBailOutJcloudsLocation(managementContext);
        jcl.tryObtainAndCheck(MutableMap.of(MIN_CORES, 2), checkerFor("fred", 16, 2));
    }

    @Test
    public void testCreateWithFlagsDirectlyAndOverride() throws Exception {
        BailOutJcloudsLocation jcl = BailOutJcloudsLocation.newBailOutJcloudsLocation(managementContext);
        jcl.tryObtainAndCheck(MutableMap.of(MIN_CORES, 2, MIN_RAM, 8), checkerFor("fred", 8, 2));
    }

    @Test
    public void testCreateWithFlagsSubLocation() throws Exception {
        BailOutJcloudsLocation jcl = BailOutJcloudsLocation.newBailOutJcloudsLocation(managementContext);
        jcl = (BailOutJcloudsLocation) jcl.newSubLocation(MutableMap.of(USER, "jon", MIN_CORES, 2));
        jcl.tryObtainAndCheck(MutableMap.of(MIN_CORES, 3), checkerFor("jon", 16, 3));
    }

    @Test
    public void testStringListToIntArray() {
        String listString = "[1, 2, 3, 4]";
        int[] intArray = new int[] {1, 2, 3, 4};
        Assert.assertEquals(JcloudsLocation.toIntArray(listString), intArray);
    }
    
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testMalformedStringListToIntArray() {
        String listString = "1, 2, 3, 4";
        JcloudsLocation.toIntArray(listString);
    }
    
    @Test
    public void testEmptyStringListToIntArray() {
        String listString = "[]";
        int[] intArray = new int[] {};
        Assert.assertEquals(JcloudsLocation.toIntArray(listString), intArray);
    }
    
    @Test
    public void testIntArrayToIntArray() {
        int[] intArray = new int[] {1, 2, 3, 4};
        Assert.assertEquals(JcloudsLocation.toIntArray(intArray), intArray);
    }
    
    @Test
    public void testObjectArrayToIntArray() {
        Object[] longArray = new Object[] {1, 2, 3, 4};
        int[] intArray = new int[] {1, 2, 3, 4};
        Assert.assertEquals(JcloudsLocation.toIntArray(longArray), intArray);
    }
    
    @Test(expectedExceptions = ClassCastException.class)
    public void testInvalidObjectArrayToIntArray() {
        String[] stringArray = new String[] {"1", "2", "3"};
        JcloudsLocation.toIntArray(stringArray);
    }

    @Test
    public void testVMCreationIsRetriedOnFailure() {
        final AtomicInteger count = new AtomicInteger();
        Function<ConfigBag, Void> countingInterceptor = new Function<ConfigBag, Void>() {
            @Override public Void apply(ConfigBag input) {
                count.incrementAndGet();
                return null;
            }
        };
        BailOutJcloudsLocation loc = BailOutJcloudsLocation.newBailOutJcloudsLocation(managementContext, ImmutableMap.<ConfigKey<?>, Object>of(
                MACHINE_CREATE_ATTEMPTS, 3,
                BailOutJcloudsLocation.BUILD_TEMPLATE_INTERCEPTOR, countingInterceptor));
        loc.tryObtain();
        Assert.assertEquals(count.get(), 3);
    }

    @Test(groups={"Live", "Live-sanity"})
    public void testCreateWithInboundPorts() {
        BailOutJcloudsLocation jcloudsLocation = BailOutJcloudsLocation.newBailOutJcloudsLocationForLiveTest(managementContext);
        jcloudsLocation = (BailOutJcloudsLocation) jcloudsLocation.newSubLocation(MutableMap.of());
        jcloudsLocation.tryObtainAndCheck(MutableMap.of(), templateCheckerFor("[22, 80, 9999]"));
        int[] ports = new int[] {22, 80, 9999};
        Assert.assertEquals(jcloudsLocation.getTemplate().getOptions().getInboundPorts(), ports);
    }
    
    @Test(groups={"Live", "Live-sanity"})
    public void testCreateWithInboundPortsOverride() {
        BailOutJcloudsLocation jcloudsLocation = BailOutJcloudsLocation.newBailOutJcloudsLocationForLiveTest(managementContext);
        jcloudsLocation = (BailOutJcloudsLocation) jcloudsLocation.newSubLocation(MutableMap.of());
        jcloudsLocation.tryObtainAndCheck(MutableMap.of(INBOUND_PORTS, "[23, 81, 9998]"), templateCheckerFor("[23, 81, 9998]"));
        int[] ports = new int[] {23, 81, 9998};
        Assert.assertEquals(jcloudsLocation.getTemplate().getOptions().getInboundPorts(), ports);
    }

    @Test
    public void testCreateWithMaxConcurrentCallsUnboundedByDefault() throws Exception {
        final int numCalls = 20;
        ConcurrencyTracker interceptor = new ConcurrencyTracker();
        ExecutorService executor = Executors.newCachedThreadPool();

        try {
            final BailOutJcloudsLocation jcloudsLocation = BailOutJcloudsLocation.newBailOutJcloudsLocation(
                    managementContext, ImmutableMap.<ConfigKey<?>, Object>of(
                            BailOutJcloudsLocation.BUILD_TEMPLATE_INTERCEPTOR, interceptor));
            for (int i = 0; i < numCalls; i++) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        jcloudsLocation.tryObtain();
                    }
                });
            }
            interceptor.assertCallCountEventually(numCalls);
            interceptor.unblock();
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test(groups="Integration") // because takes 1 sec
    public void testCreateWithMaxConcurrentCallsRespectsConfig() throws Exception {
        final int numCalls = 4;
        final int maxConcurrentCreations = 2;
        ConcurrencyTracker interceptor = new ConcurrencyTracker();
        ExecutorService executor = Executors.newCachedThreadPool();
        
        try {
            final BailOutJcloudsLocation jcloudsLocation = BailOutJcloudsLocation.newBailOutJcloudsLocation(
                    managementContext, ImmutableMap.of(
                            BailOutJcloudsLocation.BUILD_TEMPLATE_INTERCEPTOR, interceptor,
                            MAX_CONCURRENT_MACHINE_CREATIONS, maxConcurrentCreations));

            for (int i = 0; i < numCalls; i++) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        jcloudsLocation.tryObtain();
                    }
                });
            }

            interceptor.assertCallCountEventually(maxConcurrentCreations);
            interceptor.assertCallCountContinually(maxConcurrentCreations);

            interceptor.unblock();
            interceptor.assertCallCountEventually(numCalls);
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

        } finally {
            executor.shutdownNow();
        }
    }

    @Test(groups="Integration") // because takes 1 sec
    public void testCreateWithMaxConcurrentCallsAppliesToSubLocations() throws Exception {
        final int numCalls = 4;
        final int maxConcurrentCreations = 2;
        ConcurrencyTracker interceptor = new ConcurrencyTracker();
        ExecutorService executor = Executors.newCachedThreadPool();

        try {
            final BailOutJcloudsLocation jcloudsLocation = BailOutJcloudsLocation.newBailOutJcloudsLocation(
                    managementContext, ImmutableMap.of(
                            BailOutJcloudsLocation.BUILD_TEMPLATE_INTERCEPTOR, interceptor,
                            MAX_CONCURRENT_MACHINE_CREATIONS, maxConcurrentCreations));

            for (int i = 0; i < numCalls; i++) {
                final BailOutJcloudsLocation subLocation = (BailOutJcloudsLocation) jcloudsLocation.newSubLocation(MutableMap.of());
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        subLocation.tryObtain();
                    }
                });
            }

            interceptor.assertCallCountEventually(maxConcurrentCreations);
            interceptor.assertCallCountContinually(maxConcurrentCreations);

            interceptor.unblock();
            interceptor.assertCallCountEventually(numCalls);
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

        } finally {
            executor.shutdownNow();
        }
    }
    
    @Test
    public void testCreateWithCustomMachineNamer() {
        final String machineNamerClass = CustomMachineNamer.class.getName();
        BailOutJcloudsLocation jcloudsLocation = BailOutJcloudsLocation.newBailOutJcloudsLocation(
                managementContext, ImmutableMap.<ConfigKey<?>, Object>of(
                        LocationConfigKeys.CLOUD_MACHINE_NAMER_CLASS, machineNamerClass));
        jcloudsLocation.tryObtainAndCheck(ImmutableMap.of(CustomMachineNamer.MACHINE_NAME_TEMPLATE, "ignored"), new Predicate<ConfigBag>() {
            public boolean apply(ConfigBag input) {
                Assert.assertEquals(input.get(LocationConfigKeys.CLOUD_MACHINE_NAMER_CLASS), machineNamerClass);
                return true;
            }
        });
    }
    
    @Test
    public void testCreateWithCustomMachineNamerOnObtain() {
        final String machineNamerClass = CustomMachineNamer.class.getName();
        BailOutJcloudsLocation jcloudsLocation = BailOutJcloudsLocation.newBailOutJcloudsLocation(managementContext);
        ImmutableMap<ConfigKey<String>, String> flags = ImmutableMap.of(
                CustomMachineNamer.MACHINE_NAME_TEMPLATE, "ignored",
                LocationConfigKeys.CLOUD_MACHINE_NAMER_CLASS, machineNamerClass);
        jcloudsLocation.tryObtainAndCheck(flags, new Predicate<ConfigBag>() {
            public boolean apply(ConfigBag input) {
                Assert.assertEquals(input.get(LocationConfigKeys.CLOUD_MACHINE_NAMER_CLASS), machineNamerClass);
                return true;
            }
        });
    }

    public static class ConcurrencyTracker implements Function<ConfigBag,Void> {
        final AtomicInteger concurrentCallsCounter = new AtomicInteger();
        final CountDownLatch continuationLatch = new CountDownLatch(1);
        
        @Override public Void apply(ConfigBag input) {
            concurrentCallsCounter.incrementAndGet();
            try {
                continuationLatch.await();
            } catch (InterruptedException e) {
                throw Exceptions.propagate(e);
            }
            return null;
        }
        
        public void unblock() {
            continuationLatch.countDown();
        }

        public void assertCallCountEventually(final int expected) {
            Asserts.succeedsEventually(new Runnable() {
                @Override public void run() {
                    Assert.assertEquals(concurrentCallsCounter.get(), expected);
                }
            });
        }
        
        public void assertCallCountContinually(final int expected) {
            Asserts.succeedsContinually(new Runnable() {
                @Override public void run() {
                    Assert.assertEquals(concurrentCallsCounter.get(), expected);
                }
            });
        }
    }

    
    @SuppressWarnings("serial")
    public static class FakeLocalhostWithParentJcloudsLocation extends JcloudsLocation {
        public static final ConfigKey<Function<ConfigBag,Void>> BUILD_TEMPLATE_INTERCEPTOR = ConfigKeys.newConfigKey(new TypeToken<Function<ConfigBag,Void>>() {}, "buildtemplateinterceptor");
        
        ConfigBag lastConfigBag;

        public FakeLocalhostWithParentJcloudsLocation() {
            super();
        }

        public FakeLocalhostWithParentJcloudsLocation(Map<?, ?> conf) {
            super(conf);
        }

        @Override
        public JcloudsSshMachineLocation obtain(Map<?, ?> flags) throws NoMachinesAvailableException {
            JcloudsSshMachineLocation result = getManagementContext().getLocationManager().createLocation(LocationSpec.create(JcloudsSshMachineLocation.class)
                .configure("address", "127.0.0.1") 
                .configure("port", 22) 
                .configure("user", "bob")
                .configure("jcloudsParent", this));
            registerJcloudsMachineLocation("bogus", result);
            
            // explicitly invoke this customizer, to comply with tests
            for (JcloudsLocationCustomizer customizer : getCustomizers(config().getBag())) {
                customizer.customize(this, null, (JcloudsMachineLocation)result);
            }
            for (MachineLocationCustomizer customizer : getMachineCustomizers(config().getBag())) {
                customizer.customize((JcloudsMachineLocation)result);
            }

            return result;
        }
        
        @Override
        protected void releaseNode(String instanceId) {
            // no-op
        }
    }

    @Test
    public void testInheritsGeo() throws Exception {
        ConfigBag allConfig = ConfigBag.newInstance()
            .configure(IMAGE_ID, "bogus")
            .configure(CLOUD_PROVIDER, "aws-ec2")
            .configure(CLOUD_REGION_ID, "bogus")
            .configure(ACCESS_IDENTITY, "bogus")
            .configure(ACCESS_CREDENTIAL, "bogus")
            .configure(LocationConfigKeys.LATITUDE, 42d)
            .configure(LocationConfigKeys.LONGITUDE, -20d)
            .configure(MACHINE_CREATE_ATTEMPTS, 1);
        FakeLocalhostWithParentJcloudsLocation ll = managementContext.getLocationManager().createLocation(LocationSpec.create(FakeLocalhostWithParentJcloudsLocation.class).configure(allConfig.getAllConfig()));
        MachineLocation l = ll.obtain();
        log.info("loc:" +l);
        HostGeoInfo geo = HostGeoInfo.fromLocation(l);
        log.info("geo: "+geo);
        Assert.assertEquals(geo.latitude, 42d, 0.00001);
        Assert.assertEquals(geo.longitude, -20d, 0.00001);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testInheritsGeoFromLocationMetadataProperties() throws Exception {
        // in location-metadata.properties:
//        brooklyn.location.jclouds.softlayer@wdc01.latitude=38.909202
//        brooklyn.location.jclouds.softlayer@wdc01.longitude=-77.47314
        ConfigBag allConfig = ConfigBag.newInstance()
            .configure(IMAGE_ID, "bogus")
            .configure(CLOUD_PROVIDER, "softlayer")
            .configure(CLOUD_REGION_ID, "wdc01")
            .configure(ACCESS_IDENTITY, "bogus")
            .configure(ACCESS_CREDENTIAL, "bogus")
            .configure(MACHINE_CREATE_ATTEMPTS, 1);
        FakeLocalhostWithParentJcloudsLocation ll = managementContext.getLocationManager().createLocation(LocationSpec.create(FakeLocalhostWithParentJcloudsLocation.class)
            .configure(new JcloudsPropertiesFromBrooklynProperties().getJcloudsProperties("softlayer", "wdc01", null, managementContext.getBrooklynProperties()))
            .configure(allConfig.getAllConfig()));
        MachineLocation l = ll.obtain();
        log.info("loc:" +l);
        HostGeoInfo geo = HostGeoInfo.fromLocation(l);
        log.info("geo: "+geo);
        Assert.assertEquals(geo.latitude, 38.909202d, 0.00001);
        Assert.assertEquals(geo.longitude, -77.47314d, 0.00001);
    }

    @Test
    public void testInvokesCustomizerCallbacks() throws Exception {
        JcloudsLocationCustomizer customizer = Mockito.mock(JcloudsLocationCustomizer.class);
        MachineLocationCustomizer machineCustomizer = Mockito.mock(MachineLocationCustomizer.class);
//        Mockito.when(customizer.customize(Mockito.any(JcloudsLocation.class), Mockito.any(ComputeService.class), Mockito.any(JcloudsSshMachineLocation.class)));
        ConfigBag allConfig = ConfigBag.newInstance()
            .configure(CLOUD_PROVIDER, "aws-ec2")
            .configure(ACCESS_IDENTITY, "bogus")
            .configure(ACCESS_CREDENTIAL, "bogus")
            .configure(JcloudsLocationConfig.JCLOUDS_LOCATION_CUSTOMIZERS, ImmutableList.of(customizer))
            .configure(JcloudsLocation.MACHINE_LOCATION_CUSTOMIZERS, ImmutableList.of(machineCustomizer))
            .configure(MACHINE_CREATE_ATTEMPTS, 1);
        FakeLocalhostWithParentJcloudsLocation ll = managementContext.getLocationManager().createLocation(LocationSpec.create(FakeLocalhostWithParentJcloudsLocation.class).configure(allConfig.getAllConfig()));
        JcloudsMachineLocation l = (JcloudsMachineLocation)ll.obtain();
        Mockito.verify(customizer, Mockito.times(1)).customize(ll, null, l);
        Mockito.verify(customizer, Mockito.never()).preRelease(l);
        Mockito.verify(customizer, Mockito.never()).postRelease(l);
        Mockito.verify(machineCustomizer, Mockito.times(1)).customize(l);
        Mockito.verify(machineCustomizer, Mockito.never()).preRelease(l);
        
        ll.release(l);
        Mockito.verify(customizer, Mockito.times(1)).preRelease(l);
        Mockito.verify(customizer, Mockito.times(1)).postRelease(l);
        Mockito.verify(machineCustomizer, Mockito.times(1)).preRelease(l);
    }

    // now test creating users
    
    protected String getCreateUserStatementsFor(Map<ConfigKey<?>,?> config) {
        BailOutJcloudsLocation jl = BailOutJcloudsLocation.newBailOutJcloudsLocation(
                managementContext, MutableMap.<ConfigKey<?>, Object>builder()
                        .put(JcloudsLocationConfig.LOGIN_USER, "root").put(JcloudsLocationConfig.LOGIN_USER_PASSWORD, "m0ck")
                        .put(JcloudsLocationConfig.USER, "bob").put(JcloudsLocationConfig.LOGIN_USER_PASSWORD, "b0b")
                        .putAll(config).build());

        UserCreation creation = jl.createUserStatements(null, jl.config().getBag());
        return new StatementList(creation.statements).render(OsFamily.UNIX);
    }
    
    @Test
    public void testDisablesRoot() {
        String statements = getCreateUserStatementsFor(ImmutableMap.<ConfigKey<?>, Object>of());
        Assert.assertTrue(statements.contains("PermitRootLogin"), "Error:\n"+statements);
        Assert.assertTrue(statements.matches("(?s).*sudoers.*useradd.*bob.*wheel.*"), "Error:\n"+statements);
    }

    @Test
    public void testDisableRootFalse() {
        String statements = getCreateUserStatementsFor(ImmutableMap.<ConfigKey<?>, Object>of(
                JcloudsLocationConfig.DISABLE_ROOT_AND_PASSWORD_SSH, false));
        Assert.assertFalse(statements.contains("PermitRootLogin"), "Error:\n"+statements);
        Assert.assertTrue(statements.matches("(?s).*sudoers.*useradd.*bob.*wheel.*"), "Error:\n"+statements);
    }
    
    @Test
    public void testDisableRootAndSudoFalse() {
        String statements = getCreateUserStatementsFor(ImmutableMap.<ConfigKey<?>, Object>of(
            JcloudsLocationConfig.DISABLE_ROOT_AND_PASSWORD_SSH, false,
            JcloudsLocationConfig.GRANT_USER_SUDO, false));
        Assert.assertFalse(statements.contains("PermitRootLogin"), "Error:\n"+statements);
        Assert.assertFalse(statements.matches("(?s).*sudoers.*useradd.*bob.*wheel.*"), "Error:\n"+statements);
    }

    // TODO more tests, where flags come in from resolver, named locations, etc

}

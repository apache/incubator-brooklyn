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
package org.apache.brooklyn.entity.software.base;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.mgmt.EntityManager;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.mgmt.TaskAdaptable;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.drivers.BasicEntityDriverManager;
import org.apache.brooklyn.core.entity.drivers.ReflectiveEntityDriverFactory;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.effector.core.Effectors;
import org.apache.brooklyn.entity.software.base.AbstractSoftwareProcessSshDriver;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcess;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcessDriver;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcessImpl;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.SoftwareProcessDriver;
import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;
import org.apache.brooklyn.entity.software.base.SoftwareProcess.RestartSoftwareParameters;
import org.apache.brooklyn.entity.software.base.SoftwareProcess.StopSoftwareParameters;
import org.apache.brooklyn.entity.software.base.SoftwareProcess.RestartSoftwareParameters.RestartMachineMode;
import org.apache.brooklyn.entity.software.base.SoftwareProcess.StopSoftwareParameters.StopMode;
import org.apache.brooklyn.entity.software.base.lifecycle.MachineLifecycleEffectorTasksTest;
import org.apache.brooklyn.sensor.core.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.PropagatedRuntimeException;
import org.apache.brooklyn.util.net.UserAndHostAndPort;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.jclouds.util.Throwables2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import org.apache.brooklyn.location.byon.FixedListMachineProvisioningLocation;
import org.apache.brooklyn.location.core.Locations;
import org.apache.brooklyn.location.core.SimulatedLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;


public class SoftwareProcessEntityTest extends BrooklynAppUnitTestSupport {

    // NB: These tests don't actually require ssh to localhost -- only that 'localhost' resolves.

    private static final Logger LOG = LoggerFactory.getLogger(SoftwareProcessEntityTest.class);

    private SshMachineLocation machine;
    private FixedListMachineProvisioningLocation<SshMachineLocation> loc;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = getLocation();
    }

    @SuppressWarnings("unchecked")
    private FixedListMachineProvisioningLocation<SshMachineLocation> getLocation() {
        FixedListMachineProvisioningLocation<SshMachineLocation> loc = mgmt.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class));
        machine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", "localhost"));
        loc.addMachine(machine);
        return loc;
    }

    @Test
    public void testSetsMachineAttributes() throws Exception {
        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class));
        entity.start(ImmutableList.of(loc));
        
        assertEquals(entity.getAttribute(SoftwareProcess.HOSTNAME), machine.getAddress().getHostName());
        assertEquals(entity.getAttribute(SoftwareProcess.ADDRESS), machine.getAddress().getHostAddress());
        assertEquals(entity.getAttribute(Attributes.SSH_ADDRESS), UserAndHostAndPort.fromParts(machine.getUser(), machine.getAddress().getHostName(), machine.getPort()));
        assertEquals(entity.getAttribute(SoftwareProcess.PROVISIONING_LOCATION), loc);
    }

    @Test
    public void testProcessTemplateWithExtraSubstitutions() throws Exception {
        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class));
        entity.start(ImmutableList.of(loc));
        SimulatedDriver driver = (SimulatedDriver) entity.getDriver();
        Map<String,String> substitutions = MutableMap.of("myname","peter");
        String result = driver.processTemplate("/org/apache/brooklyn/entity/software/base/template_with_extra_substitutions.txt",substitutions);
        Assert.assertTrue(result.contains("peter"));
    }

    @Test
    public void testInstallDirAndRunDir() throws Exception {
        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class)
            .configure(BrooklynConfigKeys.ONBOX_BASE_DIR, "/tmp/brooklyn-foo"));

        entity.start(ImmutableList.of(loc));

        Assert.assertEquals(entity.getAttribute(SoftwareProcess.INSTALL_DIR), "/tmp/brooklyn-foo/installs/MyService");
        Assert.assertEquals(entity.getAttribute(SoftwareProcess.RUN_DIR), "/tmp/brooklyn-foo/apps/"+entity.getApplicationId()+"/entities/MyService_"+entity.getId());
    }

    @Test
    public void testInstallDirAndRunDirUsingTilde() throws Exception {
        String dataDirName = ".brooklyn-foo"+Strings.makeRandomId(4);
        String dataDir = "~/"+dataDirName;
        String resolvedDataDir = Os.mergePaths(Os.home(), dataDirName);
        
        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class)
            .configure(BrooklynConfigKeys.ONBOX_BASE_DIR, dataDir));

        entity.start(ImmutableList.of(loc));

        Assert.assertEquals(Os.nativePath(entity.getAttribute(SoftwareProcess.INSTALL_DIR)),
                            Os.nativePath(Os.mergePaths(resolvedDataDir, "installs/MyService")));
        Assert.assertEquals(Os.nativePath(entity.getAttribute(SoftwareProcess.RUN_DIR)),
                            Os.nativePath(Os.mergePaths(resolvedDataDir, "apps/"+entity.getApplicationId()+"/entities/MyService_"+entity.getId())));
    }

    protected <T extends MyService> void doStartAndCheckVersion(Class<T> type, String expectedLabel, ConfigBag config) {
        MyService entity = app.createAndManageChild(EntitySpec.create(type)
            .configure(BrooklynConfigKeys.ONBOX_BASE_DIR, "/tmp/brooklyn-foo")
            .configure(config.getAllConfigAsConfigKeyMap()));
        entity.start(ImmutableList.of(loc));
        Assert.assertEquals(entity.getAttribute(SoftwareProcess.INSTALL_DIR), "/tmp/brooklyn-foo/installs/"
            + expectedLabel);
    }
    
    @Test
    public void testCustomInstallDir0() throws Exception {
        doStartAndCheckVersion(MyService.class, "MyService", ConfigBag.newInstance());
    }
    @Test
    public void testCustomInstallDir1() throws Exception {
        doStartAndCheckVersion(MyService.class, "MyService_9.9.8", ConfigBag.newInstance()
            .configure(SoftwareProcess.SUGGESTED_VERSION, "9.9.8"));
    }
    @Test
    public void testCustomInstallDir2() throws Exception {
        doStartAndCheckVersion(MyService.class, "MySvc_998", ConfigBag.newInstance()
            .configure(SoftwareProcess.INSTALL_UNIQUE_LABEL, "MySvc_998"));
    }
    @Test
    public void testCustomInstallDir3() throws Exception {
        doStartAndCheckVersion(MyServiceWithVersion.class, "MyServiceWithVersion_9.9.9", ConfigBag.newInstance());
    }
    @Test
    public void testCustomInstallDir4() throws Exception {
        doStartAndCheckVersion(MyServiceWithVersion.class, "MyServiceWithVersion_9.9.7", ConfigBag.newInstance()
            .configure(SoftwareProcess.SUGGESTED_VERSION, "9.9.7"));
    }
    @Test
    public void testCustomInstallDir5() throws Exception {
        doStartAndCheckVersion(MyServiceWithVersion.class, "MyServiceWithVersion_9.9.9_NaCl", ConfigBag.newInstance()
            .configure(ConfigKeys.newStringConfigKey("salt"), "NaCl"));
    }

    @Test
    public void testBasicSoftwareProcessEntityLifecycle() throws Exception {
        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class));
        entity.start(ImmutableList.of(loc));
        SimulatedDriver d = (SimulatedDriver) entity.getDriver();
        Assert.assertTrue(d.isRunning());
        entity.stop();
        Assert.assertEquals(d.events, ImmutableList.of("setup", "copyInstallResources", "install", "customize", "copyRuntimeResources", "launch", "stop"));
        assertFalse(d.isRunning());
    }
    
    @Test
    public void testBasicSoftwareProcessRestarts() throws Exception {
        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class));
        entity.start(ImmutableList.of(loc));
        SimulatedDriver d = (SimulatedDriver) entity.getDriver();
        Assert.assertTrue(d.isRunning());
        
        // this will cause restart to fail if it attempts to replace the machine
        loc.removeMachine(Locations.findUniqueSshMachineLocation(entity.getLocations()).get());
        
        // with defaults, it won't reboot machine
        d.events.clear();
        entity.restart();
        assertEquals(d.events, ImmutableList.of("stop", "launch"));

        // but here, it will try to reboot, and fail because there is no machine available
        TaskAdaptable<Void> t1 = Entities.submit(entity, Effectors.invocation(entity, Startable.RESTART, 
                ConfigBag.newInstance().configure(RestartSoftwareParameters.RESTART_MACHINE_TYPED, RestartMachineMode.TRUE)));
        t1.asTask().blockUntilEnded(Duration.TEN_SECONDS);
        if (!t1.asTask().isError()) {
            Assert.fail("Should have thrown error during "+t1+" because no more machines available at "+loc);
        }

        // now it has a machine, so reboot should succeed
        SshMachineLocation machine2 = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
            .configure("address", "localhost"));
        loc.addMachine(machine2);
        TaskAdaptable<Void> t2 = Entities.submit(entity, Effectors.invocation(entity, Startable.RESTART, 
            ConfigBag.newInstance().configure(RestartSoftwareParameters.RESTART_MACHINE_TYPED, RestartMachineMode.TRUE)));
        t2.asTask().get();
        
        assertFalse(d.isRunning());
    }

    @Test
    public void testBasicSoftwareProcessStopsEverything() throws Exception {
        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class));
        entity.start(ImmutableList.of(loc));
        SimulatedDriver d = (SimulatedDriver) entity.getDriver();
        Location machine = Iterables.getOnlyElement(entity.getLocations());

        d.events.clear();
        entity.stop();
        assertEquals(d.events, ImmutableList.of("stop"));
        assertEquals(entity.getLocations().size(), 0);
        assertTrue(loc.getAvailable().contains(machine));
    }

    @Test
    public void testBasicSoftwareProcessStopEverythingExplicitly() throws Exception {
        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class));
        entity.start(ImmutableList.of(loc));
        SimulatedDriver d = (SimulatedDriver) entity.getDriver();
        Location machine = Iterables.getOnlyElement(entity.getLocations());
        d.events.clear();

        TaskAdaptable<Void> t1 = Entities.submit(entity, Effectors.invocation(entity, Startable.STOP,
                ConfigBag.newInstance().configure(StopSoftwareParameters.STOP_MACHINE_MODE, StopSoftwareParameters.StopMode.IF_NOT_STOPPED)));
        t1.asTask().get();

        assertEquals(d.events, ImmutableList.of("stop"));
        assertEquals(entity.getLocations().size(), 0);
        assertTrue(loc.getAvailable().contains(machine));
    }

    @Test
    public void testBasicSoftwareProcessStopsProcess() throws Exception {
        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class));
        entity.start(ImmutableList.of(loc));
        SimulatedDriver d = (SimulatedDriver) entity.getDriver();
        Location machine = Iterables.getOnlyElement(entity.getLocations());
        d.events.clear();

        TaskAdaptable<Void> t1 = Entities.submit(entity, Effectors.invocation(entity, Startable.STOP,
                ConfigBag.newInstance().configure(StopSoftwareParameters.STOP_MACHINE_MODE, StopSoftwareParameters.StopMode.NEVER)));
        t1.asTask().get(10, TimeUnit.SECONDS);

        assertEquals(d.events, ImmutableList.of("stop"));
        assertEquals(ImmutableList.copyOf(entity.getLocations()), ImmutableList.of(machine));
        assertFalse(loc.getAvailable().contains(machine));
    }
    
    @Test(groups = "Integration")
    public void testBasicSoftwareProcessStopAllModes() throws Exception {
        for (boolean isEntityStopped : new boolean[] {true, false}) {
            for (StopMode stopProcessMode : StopMode.values()) {
                for (StopMode stopMachineMode : StopMode.values()) {
                    try {
                        testBasicSoftwareProcessStopModes(stopProcessMode, stopMachineMode, isEntityStopped);
                    } catch (Exception e) {
                        String msg = "stopProcessMode: " + stopProcessMode + ", stopMachineMode: " + stopMachineMode + ", isEntityStopped: " + isEntityStopped;
                        throw new PropagatedRuntimeException(msg, e);
                    }
                }
            }
        }
    }
    
    @Test
    public void testBasicSoftwareProcessStopSomeModes() throws Exception {
        for (boolean isEntityStopped : new boolean[] {true, false}) {
            StopMode stopProcessMode = StopMode.IF_NOT_STOPPED;
            StopMode stopMachineMode = StopMode.IF_NOT_STOPPED;
            try {
                testBasicSoftwareProcessStopModes(stopProcessMode, stopMachineMode, isEntityStopped);
            } catch (Exception e) {
                String msg = "stopProcessMode: " + stopProcessMode + ", stopMachineMode: " + stopMachineMode + ", isEntityStopped: " + isEntityStopped;
                throw new PropagatedRuntimeException(msg, e);
            }
        }
    }
    
    private void testBasicSoftwareProcessStopModes(StopMode stopProcessMode, StopMode stopMachineMode, boolean isEntityStopped) throws Exception {
        FixedListMachineProvisioningLocation<SshMachineLocation> l = getLocation();
        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class));
        entity.start(ImmutableList.of(l));
        SimulatedDriver d = (SimulatedDriver) entity.getDriver();
        Location machine = Iterables.getOnlyElement(entity.getLocations());
        d.events.clear();

        if (isEntityStopped) {
            ((EntityInternal)entity).setAttribute(ServiceStateLogic.SERVICE_STATE_ACTUAL, Lifecycle.STOPPED);
        }

        TaskAdaptable<Void> t1 = Entities.submit(entity, Effectors.invocation(entity, Startable.STOP,
                ConfigBag.newInstance()
                    .configure(StopSoftwareParameters.STOP_PROCESS_MODE, stopProcessMode)
                    .configure(StopSoftwareParameters.STOP_MACHINE_MODE, stopMachineMode)));
        t1.asTask().get(10, TimeUnit.SECONDS);

        if (MachineLifecycleEffectorTasksTest.canStop(stopProcessMode, isEntityStopped)) {
            assertEquals(d.events, ImmutableList.of("stop"));
        } else {
            assertTrue(d.events.isEmpty());
        }
        if (MachineLifecycleEffectorTasksTest.canStop(stopMachineMode, machine == null)) {
            assertTrue(entity.getLocations().isEmpty());
            assertTrue(l.getAvailable().contains(machine));
        } else {
            assertEquals(ImmutableList.copyOf(entity.getLocations()), ImmutableList.of(machine));
            assertFalse(l.getAvailable().contains(machine));
        }
    }

    @Test
    public void testShutdownIsIdempotent() throws Exception {
        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class));
        entity.start(ImmutableList.of(loc));
        entity.stop();
        
        entity.stop();
    }
    
    @Test
    public void testReleaseEvenIfErrorDuringStart() throws Exception {
        MyServiceImpl entity = new MyServiceImpl(app) {
            @Override public Class<?> getDriverInterface() {
                return SimulatedFailOnStartDriver.class;
            }
        };
        Entities.manage(entity);
        
        try {
            entity.start(ImmutableList.of(loc));
            Assert.fail();
        } catch (Exception e) {
            IllegalStateException cause = Throwables2.getFirstThrowableOfType(e, IllegalStateException.class);
            if (cause == null || !cause.toString().contains("Simulating start error")) throw e; 
        }
        
        try {
            entity.stop();
        } catch (Exception e) {
            // Keep going
            LOG.info("Error during stop, after simulating error during start", e);
        }
        Assert.assertEquals(loc.getAvailable(), ImmutableSet.of(machine));
        Entities.unmanage(entity);
    }

    @SuppressWarnings("rawtypes")
    public void doTestReleaseEvenIfErrorDuringStop(final Class driver) throws Exception {
        MyServiceImpl entity = new MyServiceImpl(app) {
            @Override public Class<?> getDriverInterface() {
                return driver;
            }
        };
        Entities.manage(entity);
        
        entity.start(ImmutableList.of(loc));
        Task<Void> t = entity.invoke(Startable.STOP);
        t.blockUntilEnded();
        
        assertFalse(t.isError(), "Expected parent to succeed, not fail with " + Tasks.getError(t));
        Iterator<Task<?>> failures;
        failures = Tasks.failed(Tasks.descendants(t, true)).iterator();
        Assert.assertTrue(failures.hasNext(), "Expected error in descendants");
        failures = Tasks.failed(Tasks.children(t)).iterator();
        Assert.assertTrue(failures.hasNext(), "Expected error in child");
        Throwable e = Tasks.getError(failures.next());
        if (e == null || !e.toString().contains("Simulating stop error")) 
            Assert.fail("Wrong error", e);

        Assert.assertEquals(loc.getAvailable(), ImmutableSet.of(machine), "Expected location to be available again");

        Entities.unmanage(entity);
    }

    @Test
    public void testReleaseEvenIfErrorDuringStop() throws Exception {
        doTestReleaseEvenIfErrorDuringStop(SimulatedFailOnStopDriver.class);
    }
    
    @Test
    public void testReleaseEvenIfChildErrorDuringStop() throws Exception {
        doTestReleaseEvenIfErrorDuringStop(SimulatedFailInChildOnStopDriver.class);
    }

    @Test
    public void testDoubleStopEntity() {
        ReflectiveEntityDriverFactory f = ((BasicEntityDriverManager)mgmt.getEntityDriverManager()).getReflectiveDriverFactory();
        f.addClassFullNameMapping(EmptySoftwareProcessDriver.class.getName(), MinimalEmptySoftwareProcessTestDriver.class.getName());

        // Second stop on SoftwareProcess will return early, while the first stop is still in progress
        // This causes the app to shutdown prematurely, leaking machines.
        EntityManager emgr = mgmt.getEntityManager();
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class);
        TestApplication app = emgr.createEntity(appSpec);
        emgr.manage(app);
        EntitySpec<?> latchEntitySpec = EntitySpec.create(EmptySoftwareProcess.class);
        Entity entity = app.createAndManageChild(latchEntitySpec);

        final ReleaseLatchLocation loc = mgmt.getLocationManager().createLocation(LocationSpec.create(ReleaseLatchLocation.class));
        try {
            app.start(ImmutableSet.of(loc));
            EntityTestUtils.assertAttributeEquals(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
    
            final Task<Void> firstStop = entity.invoke(Startable.STOP, ImmutableMap.<String, Object>of());
            // Wait until first task tries to release the location, at this point the entity's reference 
            // to the location is already cleared.
            Asserts.succeedsEventually(new Runnable() {
                @Override
                public void run() {
                    assertTrue(loc.isBlocked());
                }
            });
    
            // Subsequent stops will end quickly - no location to release,
            // while the first one is still releasing the machine.
            final Task<Void> secondStop = entity.invoke(Startable.STOP, ImmutableMap.<String, Object>of());;
            Asserts.succeedsEventually(new Runnable() {
                @Override
                public void run() {
                    assertTrue(secondStop.isDone());
                }
            });
    
            // Entity state is STOPPED even though first location is still releasing
            EntityTestUtils.assertAttributeEquals(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STOPPED);
            Asserts.succeedsContinually(new Runnable() {
                @Override
                public void run() {
                    assertFalse(firstStop.isDone());
                }
            });

            loc.unblock();

            // After the location is released, first task ends as well.
            EntityTestUtils.assertAttributeEquals(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STOPPED);
            Asserts.succeedsEventually(new Runnable() {
                @Override
                public void run() {
                    assertTrue(firstStop.isDone());
                }
            });

        } finally {
            loc.unblock();
        }

    }

    @Test
    public void testDoubleStopApp() {
        ReflectiveEntityDriverFactory f = ((BasicEntityDriverManager)mgmt.getEntityDriverManager()).getReflectiveDriverFactory();
        f.addClassFullNameMapping(EmptySoftwareProcessDriver.class.getName(), MinimalEmptySoftwareProcessTestDriver.class.getName());

        // Second stop on SoftwareProcess will return early, while the first stop is still in progress
        // This causes the app to shutdown prematurely, leaking machines.
        EntityManager emgr = mgmt.getEntityManager();
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class);
        final TestApplication app = emgr.createEntity(appSpec);
        emgr.manage(app);
        EntitySpec<?> latchEntitySpec = EntitySpec.create(EmptySoftwareProcess.class);
        final Entity entity = app.createAndManageChild(latchEntitySpec);

        final ReleaseLatchLocation loc = mgmt.getLocationManager().createLocation(LocationSpec.create(ReleaseLatchLocation.class));
        try {
            app.start(ImmutableSet.of(loc));
            EntityTestUtils.assertAttributeEquals(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
    
            final Task<Void> firstStop = app.invoke(Startable.STOP, ImmutableMap.<String, Object>of());
            // Wait until first task tries to release the location, at this point the entity's reference 
            // to the location is already cleared.
            Asserts.succeedsEventually(new Runnable() {
                @Override
                public void run() {
                    assertTrue(loc.isBlocked());
                }
            });
    
            // Subsequent stops will end quickly - no location to release,
            // while the first one is still releasing the machine.
            final Task<Void> secondStop = app.invoke(Startable.STOP, ImmutableMap.<String, Object>of());;
            Asserts.succeedsEventually(new Runnable() {
                @Override
                public void run() {
                    assertTrue(secondStop.isDone());
                }
            });
    
            // Since second stop succeeded the app will get unmanaged.
            Asserts.succeedsEventually(new Runnable() {
                @Override
                public void run() {
                    assertTrue(!Entities.isManaged(entity));
                    assertTrue(!Entities.isManaged(app));
                }
            });
    
            // Unmanage will cancel the first task
            Asserts.succeedsEventually(new Runnable() {
                @Override
                public void run() {
                    assertTrue(firstStop.isDone());
                }
            });
        } finally {
            // We still haven't unblocked the location release, but entity is already unmanaged.
            // Double STOP on an application could leak locations!!!
            loc.unblock();
        }
    }

    @Test
    public void testOpenPortsWithPortRangeConfig() throws Exception {
        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class)
            .configure("http.port", "9999+"));
        Assert.assertTrue(entity.getRequiredOpenPorts().contains(9999));
    }

    @ImplementedBy(MyServiceImpl.class)
    public interface MyService extends SoftwareProcess {
        PortAttributeSensorAndConfigKey HTTP_PORT = Attributes.HTTP_PORT;
        public SoftwareProcessDriver getDriver();
        public Collection<Integer> getRequiredOpenPorts();
    }

    public static class MyServiceImpl extends SoftwareProcessImpl implements MyService {
        public MyServiceImpl() {}
        public MyServiceImpl(Entity parent) { super(parent); }

        @Override
        protected void initEnrichers() {
            // Don't add enrichers messing with the SERVICE_UP state - we are setting it manually
        }

        @Override
        public Class<?> getDriverInterface() {
            return SimulatedDriver.class;
        }

        @Override
        public Collection<Integer> getRequiredOpenPorts() {
            return super.getRequiredOpenPorts();
        }
    }

    @ImplementedBy(MyServiceWithVersionImpl.class)
    public interface MyServiceWithVersion extends MyService {
        public static ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "9.9.9");
    }

    public static class MyServiceWithVersionImpl extends MyServiceImpl implements MyServiceWithVersion {
        public MyServiceWithVersionImpl() {}
        public MyServiceWithVersionImpl(Entity parent) { super(parent); }
    }

    public static class SimulatedFailOnStartDriver extends SimulatedDriver {
        public SimulatedFailOnStartDriver(EntityLocal entity, SshMachineLocation machine) {
            super(entity, machine);
        }
        
        @Override
        public void install() {
            throw new IllegalStateException("Simulating start error");
        }
    }
    
    public static class SimulatedFailOnStopDriver extends SimulatedDriver {
        public SimulatedFailOnStopDriver(EntityLocal entity, SshMachineLocation machine) {
            super(entity, machine);
        }
        
        @Override
        public void stop() {
            throw new IllegalStateException("Simulating stop error");
        }
    }
    
    public static class SimulatedFailInChildOnStopDriver extends SimulatedDriver {
        public SimulatedFailInChildOnStopDriver(EntityLocal entity, SshMachineLocation machine) {
            super(entity, machine);
        }
        
        @Override
        public void stop() {
            DynamicTasks.queue(Tasks.fail("Simulating stop error in child", null));
        }
    }
    
    public static class SimulatedDriver extends AbstractSoftwareProcessSshDriver {
        public List<String> events = new ArrayList<String>();
        private volatile boolean launched = false;
        
        public SimulatedDriver(EntityLocal entity, SshMachineLocation machine) {
            super(entity, machine);
        }

        @Override
        public boolean isRunning() {
            return launched;
        }
    
        @Override
        public void stop() {
            events.add("stop");
            launched = false;
            entity.setAttribute(Startable.SERVICE_UP, false);
            entity.setAttribute(SoftwareProcess.SERVICE_STATE_ACTUAL, Lifecycle.STOPPED);
        }
    
        @Override
        public void kill() {
            events.add("kill");
            launched = false;
            entity.setAttribute(Startable.SERVICE_UP, false);
        }
    
        @Override
        public void install() {
            events.add("install");
            entity.setAttribute(SoftwareProcess.SERVICE_STATE_ACTUAL, Lifecycle.STARTING);
        }
    
        @Override
        public void customize() {
            events.add("customize");
        }
    
        @Override
        public void launch() {
            events.add("launch");
            launched = true;
            entity.setAttribute(Startable.SERVICE_UP, true);
            entity.setAttribute(SoftwareProcess.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        }

        @Override
        public void setup() {
            events.add("setup");
        }

        @Override
        public void copyInstallResources() {
            events.add("copyInstallResources");
        }

        @Override
        public void copyRuntimeResources() {
            events.add("copyRuntimeResources");
        }

        @Override
        public void runPreInstallCommand(String command) { }

        @Override
        public void runPostInstallCommand(String command) { }

        @Override
        public void runPreLaunchCommand(String command) { }

        @Override
        public void runPostLaunchCommand(String command) { }

        @Override
        protected String getInstallLabelExtraSalt() {
            return (String)getEntity().getConfigRaw(ConfigKeys.newStringConfigKey("salt"), true).or((String)null);
        }
    }

    public static class ReleaseLatchLocation extends SimulatedLocation {
        private static final long serialVersionUID = 1L;
        
        private CountDownLatch lock = new CountDownLatch(1);
        private volatile boolean isBlocked;

        public void unblock() {
            lock.countDown();
        }
        @Override
        public void release(MachineLocation machine) {
            super.release(machine);
            try {
                isBlocked = true;
                lock.await();
                isBlocked = false;
            } catch (InterruptedException e) {
                throw Exceptions.propagate(e);
            }
        }

        public boolean isBlocked() {
            return isBlocked;
        }

    }

    public static class MinimalEmptySoftwareProcessTestDriver implements EmptySoftwareProcessDriver {

        private EmptySoftwareProcessImpl entity;
        private Location location;

        public MinimalEmptySoftwareProcessTestDriver(EmptySoftwareProcessImpl entity, Location location) {
            this.entity = entity;
            this.location = location;
        }

        @Override
        public Location getLocation() {
            return location;
        }

        @Override
        public EntityLocal getEntity() {
            return entity;
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public void rebind() {
        }

        @Override
        public void start() {
        }

        @Override
        public void restart() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void kill() {
        }

    }

}

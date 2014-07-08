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
package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jclouds.util.Throwables2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.FixedListMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.Task;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.net.UserAndHostAndPort;
import brooklyn.util.os.Os;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;


public class SoftwareProcessEntityTest extends BrooklynAppUnitTestSupport {

    // NB: These tests don't actually require ssh to localhost -- only that 'localhost' resolves.

    private static final Logger LOG = LoggerFactory.getLogger(SoftwareProcessEntityTest.class);

    private SshMachineLocation machine;
    private FixedListMachineProvisioningLocation<SshMachineLocation> loc;
    
    @BeforeMethod(alwaysRun=true)
    @SuppressWarnings("unchecked")
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = mgmt.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class));
        machine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", "localhost"));
        loc.addMachine(machine);
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
        String result = driver.processTemplate("/brooklyn/entity/basic/template_with_extra_substitutions.txt",substitutions);
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
        Assert.assertEquals(d.events, ImmutableList.of("install", "customize", "launch", "stop"));
        Assert.assertFalse(d.isRunning());
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
        
        Assert.assertFalse(t.isError(), "Expected parent to succeed, not fail with "+Tasks.getError(t));
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

    @ImplementedBy(MyServiceImpl.class)
    public interface MyService extends SoftwareProcess {
        public SoftwareProcessDriver getDriver();
    }

    public static class MyServiceImpl extends SoftwareProcessImpl implements MyService {
        public MyServiceImpl() {}
        public MyServiceImpl(Entity parent) { super(parent); }

        @Override
        public Class<?> getDriverInterface() { return SimulatedDriver.class; }
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
        }
        
        @Override
        protected String getInstallLabelExtraSalt() {
            return (String)getEntity().getConfigRaw(ConfigKeys.newStringConfigKey("salt"), true).or((String)null);
        }
    }
}

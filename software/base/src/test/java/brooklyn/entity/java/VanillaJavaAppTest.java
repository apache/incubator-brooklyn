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
package brooklyn.entity.java;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.net.MalformedURLException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.feed.jmx.JmxHelper;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.PortRanges;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.crypto.FluentKeySigner;
import brooklyn.util.crypto.SecureKeys;
import brooklyn.util.crypto.SslTrustUtils;
import brooklyn.util.jmx.jmxmp.JmxmpAgent;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

public class VanillaJavaAppTest {

    private static final Logger LOG = LoggerFactory.getLogger(VanillaJavaAppTest.class);
    
    private static final long TIMEOUT_MS = 10*1000;

    // Static attributes such as number of processors and start time are only polled every 60 seconds
    // so if they are not immediately available, it will be 60 seconds before they are polled again
    private static final Object LONG_TIMEOUT_MS = 61*1000;

    private static String BROOKLYN_THIS_CLASSPATH = null;
    private static Class<?> MAIN_CLASS = ExampleVanillaMain.class;
    private static Class<?> MAIN_CPU_HUNGRY_CLASS = ExampleVanillaMainCpuHungry.class;
    
    private TestApplication app;
    private LocalhostMachineProvisioningLocation loc;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        if (BROOKLYN_THIS_CLASSPATH==null) {
            BROOKLYN_THIS_CLASSPATH = ResourceUtils.create(MAIN_CLASS).getClassLoaderDir();
        }
        app = TestApplication.Factory.newManagedInstanceForTests();
        loc = new LocalhostMachineProvisioningLocation(MutableMap.of("address", "localhost"));
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testReadsConfigFromFlags() throws Exception {
        final VanillaJavaApp javaProcess = app.createAndManageChild(EntitySpec.create(VanillaJavaApp.class)
            .configure("main", "my.Main").configure("classpath", ImmutableList.of("c1", "c2"))
            .configure("args", ImmutableList.of("a1", "a2")));

        assertEquals(javaProcess.getMainClass(), "my.Main");
        assertEquals(javaProcess.getClasspath(), ImmutableList.of("c1","c2"));
        assertEquals(javaProcess.getConfig(VanillaJavaApp.ARGS), ImmutableList.of("a1", "a2"));
    }

    @Test(groups={"WIP", "Integration"})
    public void testJavaSystemProperties() throws Exception {
        final VanillaJavaApp javaProcess = app.createAndManageChild(EntitySpec.create(VanillaJavaApp.class)
            .configure("main", "my.Main").configure("classpath", ImmutableList.of("c1", "c2"))
            .configure("args", ImmutableList.of("a1", "a2")));
        ((EntityLocal)javaProcess).setConfig(UsesJava.JAVA_SYSPROPS, ImmutableMap.of("fooKey", "fooValue", "barKey", "barValue"));
        // TODO: how to test: launch standalone app that outputs system properties to stdout? Probe via JMX?
    }

    @Test(groups={"Integration"})
    public void testStartsAndStops() throws Exception {
        String main = MAIN_CLASS.getCanonicalName();
        final VanillaJavaApp javaProcess = app.createAndManageChild(EntitySpec.create(VanillaJavaApp.class)
            .configure("main", main).configure("classpath", ImmutableList.of(BROOKLYN_THIS_CLASSPATH))
            .configure("args", ImmutableList.of()));
        Entities.startManagement(app);
        app.start(ImmutableList.of(loc));
        assertEquals(javaProcess.getAttribute(VanillaJavaApp.SERVICE_STATE), Lifecycle.RUNNING);

        javaProcess.stop();
        assertEquals(javaProcess.getAttribute(VanillaJavaApp.SERVICE_STATE), Lifecycle.STOPPED);
    }

    @Test(groups={"Integration"})
    public void testHasJvmMXBeanSensorVals() throws Exception {
        String main = MAIN_CLASS.getCanonicalName();
        final VanillaJavaApp javaProcess = app.createAndManageChild(EntitySpec.create(VanillaJavaApp.class)
            .configure("main", main).configure("classpath", ImmutableList.of(BROOKLYN_THIS_CLASSPATH))
            .configure("args", ImmutableList.of()));
        Entities.startManagement(app);
        app.start(ImmutableList.of(loc));
        
        // Memory MXBean
        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                assertNotNull(javaProcess.getAttribute(VanillaJavaApp.NON_HEAP_MEMORY_USAGE));
                long init = javaProcess.getAttribute(VanillaJavaApp.INIT_HEAP_MEMORY);
                long used = javaProcess.getAttribute(VanillaJavaApp.USED_HEAP_MEMORY);
                long committed = javaProcess.getAttribute(VanillaJavaApp.COMMITTED_HEAP_MEMORY);
                long max = javaProcess.getAttribute(VanillaJavaApp.MAX_HEAP_MEMORY);
    
                assertNotNull(used);
                assertNotNull(init);
                assertNotNull(committed);
                assertNotNull(max);
                assertTrue(init <= max, String.format("init %d > max %d heap memory", init, max));
                assertTrue(used <= committed, String.format("used %d > committed %d heap memory", used, committed));
                assertTrue(committed <= max, String.format("committed %d > max %d heap memory", committed, max));
            }});
        
        // Threads MX Bean
        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                long current = javaProcess.getAttribute(VanillaJavaApp.CURRENT_THREAD_COUNT);
                long peak = javaProcess.getAttribute(VanillaJavaApp.PEAK_THREAD_COUNT);
    
                assertNotNull(current);
                assertNotNull(peak);
                assertTrue(current <= peak, String.format("current %d > peak %d thread count", current, peak));
            }});

        // Runtime MX Bean
        Asserts.succeedsEventually(MutableMap.of("timeout", LONG_TIMEOUT_MS), new Runnable() {
            public void run() {
                assertNotNull(javaProcess.getAttribute(VanillaJavaApp.START_TIME));
                assertNotNull(javaProcess.getAttribute(VanillaJavaApp.UP_TIME));
            }});
        
        // Operating System MX Bean
        Asserts.succeedsEventually(MutableMap.of("timeout", LONG_TIMEOUT_MS), new Runnable() {
            public void run() {
                assertNotNull(javaProcess.getAttribute(VanillaJavaApp.PROCESS_CPU_TIME));
                assertNotNull(javaProcess.getAttribute(VanillaJavaApp.SYSTEM_LOAD_AVERAGE));
                assertNotNull(javaProcess.getAttribute(VanillaJavaApp.AVAILABLE_PROCESSORS));
                assertNotNull(javaProcess.getAttribute(VanillaJavaApp.TOTAL_PHYSICAL_MEMORY_SIZE));
                assertNotNull(javaProcess.getAttribute(VanillaJavaApp.FREE_PHYSICAL_MEMORY_SIZE));
            }});
        // TODO work on providing useful metrics from garbage collector MX Bean
        // assertNotNull(javaProcess.getAttribute(VanillaJavaApp.GARBAGE_COLLECTION_TIME)) TODO: work on providing this
    }
    
    @Test(groups={"Integration"})
    public void testJvmMXBeanProcessCpuTimeGivesNonZeroPercentage() throws Exception {
        String main = MAIN_CPU_HUNGRY_CLASS.getCanonicalName();
        final VanillaJavaApp javaProcess = app.createAndManageChild(EntitySpec.create(VanillaJavaApp.class)
            .configure("main", main).configure("classpath", ImmutableList.of(BROOKLYN_THIS_CLASSPATH))
            .configure("args", ImmutableList.of()));
        Entities.startManagement(app);
        app.start(ImmutableList.of(loc));

        JavaAppUtils.connectJavaAppServerPolicies((EntityLocal)javaProcess);
        
        final List<Double> fractions = new CopyOnWriteArrayList<Double>();
        app.getManagementContext().getSubscriptionManager().subscribe(javaProcess, VanillaJavaApp.PROCESS_CPU_TIME_FRACTION_LAST, new SensorEventListener<Double>() {
                public void onEvent(SensorEvent<Double> event) {
                    fractions.add(event.getValue());
                }});
        
        // Expect non-trivial load to be generated by the process.
        // Expect load to be in the right order of magnitude (to ensure we haven't got a decimal point in the wrong place etc);
        // But with multi-core could get big number; and on jenkins@releng3 we once saw [11.9, 0.6, 0.5]!
        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                Iterable<Double> nonTrivialFractions = Iterables.filter(fractions, new Predicate<Double>() {
                        public boolean apply(Double input) {
                            return input > 0.01;
                        }});
                assertTrue(Iterables.size(nonTrivialFractions) > 10, "fractions="+fractions); 
            }});

        Iterable<Double> tooBigFractions = Iterables.filter(fractions, new Predicate<Double>() {
                public boolean apply(Double input) {
                    return input > 50;
                }});
        assertTrue(Iterables.isEmpty(tooBigFractions), "fractions="+fractions); 
        
        Iterable<Double> ballparkRightFractions = Iterables.filter(fractions, new Predicate<Double>() {
                public boolean apply(Double input) {
                    return input > 0.01 && input < 4;
                }});
        assertTrue(Iterables.size(ballparkRightFractions) >= (fractions.size() / 2), "fractions="+fractions);
        
        LOG.info("VanillaJavaApp->ExampleVanillaMainCpuHuntry: ProcessCpuTime fractions="+fractions);
    }

    @Test(groups={"Integration"})
    public void testStartsWithJmxPortSpecifiedInConfig() throws Exception {
        int port = 53405;
        String main = MAIN_CLASS.getCanonicalName();
        VanillaJavaApp javaProcess = app.createAndManageChild(EntitySpec.create(VanillaJavaApp.class)
            .configure("main", main).configure("classpath", ImmutableList.of(BROOKLYN_THIS_CLASSPATH))
            .configure("args", ImmutableList.of()));
        ((EntityLocal)javaProcess).setConfig(UsesJmx.JMX_PORT, PortRanges.fromInteger(port));
        Entities.startManagement(app);
        app.start(ImmutableList.of(loc));

        assertEquals(javaProcess.getAttribute(UsesJmx.JMX_PORT), (Integer)port);
    }

    // FIXME Way test was written requires JmxSensorAdapter; need to rewrite...  
    @Test(groups={"Integration", "WIP"})
    public void testStartsWithSecureJmxPortSpecifiedInConfig() throws Exception {
        int port = 53406;
        String main = MAIN_CLASS.getCanonicalName();
        final VanillaJavaApp javaProcess = app.createAndManageChild(EntitySpec.create(VanillaJavaApp.class)
            .configure("main", main).configure("classpath", ImmutableList.of(BROOKLYN_THIS_CLASSPATH))
            .configure("args", ImmutableList.of()));
        ((EntityLocal)javaProcess).setConfig(UsesJmx.JMX_PORT, PortRanges.fromInteger(port));
        ((EntityLocal)javaProcess).setConfig(UsesJmx.JMX_SSL_ENABLED, true);
        
        Entities.startManagement(app);
        app.start(ImmutableList.of(loc));
        // will fail above if JMX can't connect, but also do some add'l checks
        
        assertEquals(javaProcess.getAttribute(UsesJmx.JMX_PORT), (Integer)port);

        // good key+cert succeeds
        new AsserterForJmxConnection(javaProcess)
                .customizeSocketFactory(null, null)
                .connect();
        
        // bad cert fails
        Asserts.assertFails(new Callable<Void>() {
            public Void call() throws Exception {
                new AsserterForJmxConnection(javaProcess)
                        .customizeSocketFactory(null, new FluentKeySigner("cheater").newCertificateFor("jmx-access-key", SecureKeys.newKeyPair()))
                        .connect();
                return null;
            }});

        // bad key fails
        Asserts.assertFails(new Callable<Void>() {
            public Void call() throws Exception {
                new AsserterForJmxConnection(javaProcess)
                        .customizeSocketFactory(SecureKeys.newKeyPair().getPrivate(), null)
                        .connect();
                return null;
            }});
        
        // bad profile fails
        Asserts.assertFails(new Callable<Void>() {
            public Void call() throws Exception {
                AsserterForJmxConnection asserter = new AsserterForJmxConnection(javaProcess);
                asserter.putEnv("jmx.remote.profiles", JmxmpAgent.TLS_JMX_REMOTE_PROFILES);
                asserter.customizeSocketFactory(SecureKeys.newKeyPair().getPrivate(), null)
                        .connect();
                return null;
            }});
    }

    private static class AsserterForJmxConnection {
        final VanillaJavaApp entity;
        final JMXServiceURL url;
        final Map<String,Object> env;
        
        @SuppressWarnings("unchecked")
        public AsserterForJmxConnection(VanillaJavaApp e) throws MalformedURLException {
            this.entity = e;
            
            JmxHelper jmxHelper = new JmxHelper((EntityLocal)entity);
            this.url = new JMXServiceURL(jmxHelper.getUrl());
            this.env = Maps.newLinkedHashMap(jmxHelper.getConnectionEnvVars());
        }
        
        public JMXServiceURL getJmxUrl() throws MalformedURLException {
            return url;
        }
        
        public void putEnv(String key, Object val) {
            env.put(key, val);
        }
        
        public AsserterForJmxConnection customizeSocketFactory(PrivateKey customKey, Certificate customCert) throws Exception {
            PrivateKey key = (customKey == null) ? entity.getConfig(UsesJmx.JMX_SSL_ACCESS_KEY) : customKey;
            Certificate cert = (customCert == null) ? entity.getConfig(UsesJmx.JMX_SSL_ACCESS_CERT) : customCert;
            
            KeyStore ks = SecureKeys.newKeyStore();
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            if (key!=null) {
                ks.setKeyEntry("brooklyn-jmx-access", key, "".toCharArray(), new Certificate[] {cert});
            }
            kmf.init(ks, "".toCharArray());

            TrustManager tms =
            // TODO use root cert for trusting server
            //trustStore!=null ? SecureKeys.getTrustManager(trustStore) :
                SslTrustUtils.TRUST_ALL;

            SSLContext ctx = SSLContext.getInstance("TLSv1");
            ctx.init(kmf.getKeyManagers(), new TrustManager[] {tms}, null);
            SSLSocketFactory ssf = ctx.getSocketFactory();
            env.put(JmxmpAgent.TLS_SOCKET_FACTORY_PROPERTY, ssf);
            
            return this;
        }
        
        public JMXConnector connect() throws Exception {
            return JMXConnectorFactory.connect(getJmxUrl(), env);
        }
    }
}

package brooklyn.entity.java

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.CopyOnWriteArrayList

import javax.management.remote.JMXConnector
import javax.management.remote.JMXConnectorFactory
import javax.management.remote.JMXServiceURL
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities
import brooklyn.entity.basic.Lifecycle
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.event.adapter.JmxHelper
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.location.basic.PortRanges;
import brooklyn.test.TestUtils
import brooklyn.util.ResourceUtils
import brooklyn.util.crypto.FluentKeySigner
import brooklyn.util.crypto.SecureKeys
import brooklyn.util.crypto.SslTrustUtils
import brooklyn.util.jmx.jmxmp.JmxmpAgent

import com.google.common.base.Predicate
import com.google.common.collect.Iterables

class VanillaJavaAppTest {

    private static final Logger LOG = LoggerFactory.getLogger(VanillaJavaAppTest.class);
    
    private static final long TIMEOUT_MS = 10*1000

    private static String BROOKLYN_THIS_CLASSPATH = null;
    private static Class MAIN_CLASS = ExampleVanillaMain.class;
    private static Class MAIN_CPU_HUNGRY_CLASS = ExampleVanillaMainCpuHungry.class;
    
    AbstractApplication app
    LocalhostMachineProvisioningLocation loc

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        if (BROOKLYN_THIS_CLASSPATH==null) {
            BROOKLYN_THIS_CLASSPATH = new ResourceUtils(MAIN_CLASS).getClassLoaderDir();
        }
        app = new AbstractApplication() {}
        loc = new LocalhostMachineProvisioningLocation(address:"localhost")
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        if (app != null) Entities.destroy(app);
    }

    @Test
    public void testReadsConfigFromFlags() {
        VanillaJavaApp javaProcess = new VanillaJavaApp(parent:app, main:"my.Main", classpath:["c1", "c2"], args:["a1", "a2"])
        assertEquals(javaProcess.getMainClass(), "my.Main")
        assertEquals(javaProcess.getClasspath(), ["c1","c2"])
        assertEquals(javaProcess.getConfig(VanillaJavaApp.ARGS), ["a1", "a2"])
    }

    @Test(groups=["WIP", "Integration"])
    public void testJavaSystemProperties() {
        VanillaJavaApp javaProcess = new VanillaJavaApp(parent:app, main:"my.Main", classpath:["c1", "c2"], args:["a1", "a2"])
        javaProcess.setConfig(UsesJava.JAVA_SYSPROPS, ["fooKey":"fooValue", "barKey":"barValue"])
        // TODO: how to test: launch standalone app that outputs system properties to stdout? Probe via JMX?
    }

    @Test(groups=["Integration"])
    public void testStartsAndStops() {
        String main = MAIN_CLASS.getCanonicalName();
        VanillaJavaApp javaProcess = new VanillaJavaApp(parent:app, main:main, classpath:[BROOKLYN_THIS_CLASSPATH], args:[])
        app.start([loc])
        assertEquals(javaProcess.getAttribute(VanillaJavaApp.SERVICE_STATE), Lifecycle.RUNNING)

        javaProcess.stop()
        assertEquals(javaProcess.getAttribute(VanillaJavaApp.SERVICE_STATE), Lifecycle.STOPPED)
    }

    @Test(groups=["Integration"])
    public void testHasJvmMXBeanSensorVals() {
        String main = MAIN_CLASS.getCanonicalName();
        VanillaJavaApp javaProcess = new VanillaJavaApp(parent:app, main:main, classpath:[BROOKLYN_THIS_CLASSPATH], args:[])
        app.start([loc])
        
        // Memory MXBean
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertNotNull(javaProcess.getAttribute(VanillaJavaApp.NON_HEAP_MEMORY_USAGE))
            long init = javaProcess.getAttribute(VanillaJavaApp.INIT_HEAP_MEMORY)
            long used = javaProcess.getAttribute(VanillaJavaApp.USED_HEAP_MEMORY)
            long committed = javaProcess.getAttribute(VanillaJavaApp.COMMITTED_HEAP_MEMORY)
            long max = javaProcess.getAttribute(VanillaJavaApp.MAX_HEAP_MEMORY)

            assertNotNull(used)
            assertNotNull(init)
            assertNotNull(committed)
            assertNotNull(max)
            assertTrue(init <= max, String.format("init %d > max %d heap memory", init, max))
            assertTrue(used <= committed, String.format("used %d > committed %d heap memory", used, committed))
            assertTrue(committed <= max, String.format("committed %d > max %d heap memory", committed, max))
        }
        
        // Threads MX Bean
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            long current = javaProcess.getAttribute(VanillaJavaApp.CURRENT_THREAD_COUNT)
            long peak = javaProcess.getAttribute(VanillaJavaApp.PEAK_THREAD_COUNT)

            assertNotNull(current)
            assertNotNull(peak)
            assertTrue(current <= peak, String.format("current %d > peak %d thread count", current, peak))

            //
            assertNotNull(javaProcess.getAttribute(VanillaJavaApp.START_TIME))
            assertNotNull(javaProcess.getAttribute(VanillaJavaApp.SYSTEM_LOAD_AVERAGE))
        }

        // Runtime MX Bean
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertNotNull(javaProcess.getAttribute(VanillaJavaApp.START_TIME))
            assertNotNull(javaProcess.getAttribute(VanillaJavaApp.UP_TIME))
        }
        
        // Opeating System MX Bean
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertNotNull(javaProcess.getAttribute(VanillaJavaApp.PROCESS_CPU_TIME))
            assertNotNull(javaProcess.getAttribute(VanillaJavaApp.SYSTEM_LOAD_AVERAGE))
            assertNotNull(javaProcess.getAttribute(VanillaJavaApp.AVAILABLE_PROCESSORS))
            assertNotNull(javaProcess.getAttribute(VanillaJavaApp.TOTAL_PHYSICAL_MEMORY_SIZE))
            assertNotNull(javaProcess.getAttribute(VanillaJavaApp.FREE_PHYSICAL_MEMORY_SIZE))
        }
        
        // TODO work on providing useful metrics from garbage collector MX Bean
        // assertNotNull(javaProcess.getAttribute(VanillaJavaApp.GARBAGE_COLLECTION_TIME)) TODO: work on providing this
    }
    
    @Test(groups=["Integration"])
    public void testJvmMXBeanProcessCpuTimeGivesNonZeroPercentage() {
        String main = MAIN_CPU_HUNGRY_CLASS.getCanonicalName();
        VanillaJavaApp javaProcess = new VanillaJavaApp(parent:app, main:main, classpath:[BROOKLYN_THIS_CLASSPATH], args:[])
        app.start([loc])

        JavaAppUtils.connectJavaAppServerPolicies(javaProcess);
        
        final List<Double> fractions = new CopyOnWriteArrayList<Double>();
        app.getManagementContext().getSubscriptionManager().subscribe(javaProcess, VanillaJavaApp.PROCESS_CPU_TIME_FRACTION, new SensorEventListener<Double>() {
                public void onEvent(SensorEvent<Double> event) {
                    fractions.add(event.getValue());
                }});
        
        // Expect non-trivial load to be generated by the process.
        // Expect load to be in the right order of magnitude (to ensure we haven't got a decimal point in the wrong place etc);
        // But with multi-core could get big number; and on jenkins@releng3 we once saw [11.9, 0.6, 0.5]!
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            Iterable<Double> nonTrivialFractions = Iterables.filter(fractions, { it > 0.01 } as Predicate);
            assertTrue(Iterables.size(nonTrivialFractions) > 10, "fractions="+fractions); 
        }

        Iterable<Double> tooBigFractions = Iterables.filter(fractions, { it > 50 } as Predicate);
        assertTrue(Iterables.isEmpty(tooBigFractions), "fractions="+fractions); 
        
        Iterable<Double> ballparkRightFractions = Iterables.filter(fractions, { it > 0.01 && it < 4 } as Predicate);
        assertTrue(Iterables.size(ballparkRightFractions) >= (fractions.size() / 2), "fractions="+fractions);
        
        LOG.info("VanillaJavaApp->ExampleVanillaMainCpuHuntry: ProcessCpuTime fractions="+fractions);
    }

    @Test(groups=["Integration"])
    public void testStartsWithJmxPortSpecifiedInConfig() {
        int port = 53405;
        String main = MAIN_CLASS.getCanonicalName();
        VanillaJavaApp javaProcess = new VanillaJavaApp(parent:app, main:main, classpath:[BROOKLYN_THIS_CLASSPATH], args:[])
        javaProcess.setConfig(UsesJmx.JMX_PORT, port)
        app.start([loc])

        assertEquals(javaProcess.getAttribute(UsesJmx.JMX_PORT), port)
    }

    @Test(groups=["Integration"])
    public void testStartsWithSecureJmxPortSpecifiedInConfig() {
        int port = 53406;
        String main = MAIN_CLASS.getCanonicalName();
        VanillaJavaApp javaProcess = new VanillaJavaApp(parent:app, main:main, classpath:[BROOKLYN_THIS_CLASSPATH], args:[])
        javaProcess.setConfig(UsesJmx.JMX_PORT, port)
        javaProcess.setConfig(UsesJmx.JMX_SSL_ENABLED, true)
        
        app.start([loc])
        // will fail above if JMX can't connect, but also do some add'l checks
        
        assertEquals(javaProcess.getAttribute(UsesJmx.JMX_PORT), port);

        // good key+cert succeeds        
        new AsserterForJmxConnection(javaProcess).
            customizeSocketFactory(null, null).
            connect();
        
        // bad cert fails
        TestUtils.assertFails {
            new AsserterForJmxConnection(javaProcess).
                customizeSocketFactory(null, new FluentKeySigner("cheater").newCertificateFor("jmx-access-key", SecureKeys.newKeyPair())).
                connect();
        }

        // bad key fails
        TestUtils.assertFails {
            new AsserterForJmxConnection(javaProcess).
                customizeSocketFactory(SecureKeys.newKeyPair().getPrivate(), null).
                connect();
        }
        
        // bad profile fails
        TestUtils.assertFails {
            AsserterForJmxConnection asserter = new AsserterForJmxConnection(javaProcess);
            asserter.getEnvironment().put("jmx.remote.profiles", JmxmpAgent.TLS_JMX_REMOTE_PROFILES);
            asserter.customizeSocketFactory(SecureKeys.newKeyPair().getPrivate(), null).
                connect();
        }
        
    }

    private static class AsserterForJmxConnection {
        Entity entity;
        Map env;
        
        public AsserterForJmxConnection(Entity e) { this.entity = e; }
        
        public JmxSensorAdapter getJmxAdapter() { return entity.sensorRegistry.adapters.find({ it in JmxSensorAdapter }); }
        public JmxHelper getJmxHelper() { return getJmxAdapter().helper; }
        public String getJmxUrl() { return new JMXServiceURL(getJmxHelper().url); }
        public synchronized Map getEnvironment() {
            if (env==null) env = new LinkedHashMap(getJmxHelper().getConnectionEnvVars());
            return env; 
        }
        
        public AsserterForJmxConnection customizeSocketFactory(PrivateKey customKey, Certificate customCert) {
            PrivateKey key = customKey ?: entity.getConfig(UsesJmx.JMX_SSL_ACCESS_KEY);
            Certificate cert = customCert ?: entity.getConfig(UsesJmx.JMX_SSL_ACCESS_CERT);
            
            KeyStore ks = SecureKeys.newKeyStore();
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            if (key!=null) {
                ks.setKeyEntry("brooklyn-jmx-access", key, "".toCharArray(), [ cert ] as Certificate[]);
            }
            kmf.init(ks, "".toCharArray());

            TrustManager tms =
            // TODO use root cert for trusting server
            //trustStore!=null ? SecureKeys.getTrustManager(trustStore) :
                SslTrustUtils.TRUST_ALL;

            SSLContext ctx = SSLContext.getInstance("TLSv1");
            ctx.init(kmf.getKeyManagers(), [ tms ] as TrustManager[], null);
            SSLSocketFactory ssf = ctx.getSocketFactory();
            getEnvironment().put(JmxmpAgent.TLS_SOCKET_FACTORY_PROPERTY, ssf);
            
            return this;
        }
        
        public JMXConnector connect() { return JMXConnectorFactory.connect(new JMXServiceURL(getJmxUrl()), getEnvironment()); }
    }
    
}


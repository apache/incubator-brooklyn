package brooklyn.entity.java

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.enricher.TimeFractionDeltaEnricher
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Lifecycle
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.ResourceUtils

import com.google.common.base.Predicate
import com.google.common.collect.Iterables

class VanillaJavaAppTest {

    private static final Logger LOG = LoggerFactory.getLogger(VanillaJavaAppTest.class);
    
    private static final long TIMEOUT_MS = 10*1000
    
    private static String BROOKLYN_THIS_CLASSPATH = null;
    private static Class MAIN_CLASS = ExampleVanillaMain.class;
    private static Class MAIN_CPU_HUNGRY_CLASS = ExampleVanillaMainCpuHungry.class;
    
    AbstractApplication app
    SshMachineLocation loc
    
    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        if (BROOKLYN_THIS_CLASSPATH==null) {
            BROOKLYN_THIS_CLASSPATH = new ResourceUtils(MAIN_CLASS).getClassLoaderDir();
        }
        app = new AbstractApplication() {}
        loc = new SshMachineLocation(address:"localhost")
    }
    
    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        app?.stop()
    }
    
    @Test
    public void testReadsConfigFromFlags() {
        VanillaJavaApp javaProcess = new VanillaJavaApp(owner:app, main:"my.Main", classpath:["c1", "c2"], args:["a1", "a2"])
        assertEquals(javaProcess.getMainClass(), "my.Main")
        assertEquals(javaProcess.getClasspath(), ["c1","c2"])
        assertEquals(javaProcess.getConfig(VanillaJavaApp.ARGS), ["a1", "a2"])
    }
    
    @Test(groups=["WIP", "Integration"])
    public void testJavaSystemProperties() {
        VanillaJavaApp javaProcess = new VanillaJavaApp(owner:app, main:"my.Main", classpath:["c1", "c2"], args:["a1", "a2"])
        javaProcess.setConfig(UsesJava.JAVA_SYSPROPS, ["fooKey":"fooValue", "barKey":"barValue"])
        // TODO: how to test: launch standalone app that outputs system properties to stdout? Probe via JMX?
    }
    
    @Test(groups=["Integration"])
    public void testStartsAndStops() {
        String main = MAIN_CLASS.getCanonicalName();
        VanillaJavaApp javaProcess = new VanillaJavaApp(owner:app, main:main, classpath:[BROOKLYN_THIS_CLASSPATH], args:[])
        app.start([loc])
        assertEquals(javaProcess.getAttribute(VanillaJavaApp.SERVICE_STATE), Lifecycle.RUNNING)
        
        javaProcess.stop()
        assertEquals(javaProcess.getAttribute(VanillaJavaApp.SERVICE_STATE), Lifecycle.STOPPED)
    }
    
    @Test(groups=["Integration"])
    public void testHasJvmMXBeanSensorVals() {
        String main = MAIN_CLASS.getCanonicalName();
        VanillaJavaApp javaProcess = new VanillaJavaApp(owner:app, main:main, classpath:[BROOKLYN_THIS_CLASSPATH], args:[])
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
        VanillaJavaApp javaProcess = new VanillaJavaApp(owner:app, main:main, classpath:[BROOKLYN_THIS_CLASSPATH], args:[])
        app.start([loc])

        JavaAppUtils.connectJavaAppServerPolicies(javaProcess);
        
        final List<Double> fractions = new CopyOnWriteArrayList<Double>();
        app.getManagementContext().getSubscriptionManager().subscribe(javaProcess, VanillaJavaApp.PROCESS_CPU_TIME_FRACTION, new SensorEventListener<Double>() {
                public void onEvent(SensorEvent<Double> event) {
                    fractions.add(event.getValue());
                }});
        
        // Expect non-trivial load to be generated by the process
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            Iterable<Double> nonTrivialFractions = Iterables.filter(fractions, { it > 0.01 } as Predicate);
            assertTrue(Iterables.size(nonTrivialFractions) > 2, "fractions="+fractions); 
        }
        
        // Expect max load to not be crazy high (but if multi-core might get strangely high?!)
        Iterable<Double> tooBigFractions = Iterables.filter(fractions, { it > 4 } as Predicate);
        assertTrue(Iterables.isEmpty(tooBigFractions), "fractions="+fractions); 
        
        LOG.info("VanillaJavaApp->ExampleVanillaMainCpuHuntry: ProcessCpuTime fractions="+fractions);
    }
    
    @Test(groups=["Integration"])
    public void testStartsWithJmxPortSpecifiedInConfig() {
        String main = MAIN_CLASS.getCanonicalName();
        VanillaJavaApp javaProcess = new VanillaJavaApp(owner:app, main:main, classpath:[BROOKLYN_THIS_CLASSPATH], args:[])
        javaProcess.setConfig(UsesJmx.JMX_PORT, 54321)
        app.start([loc])
        
        assertEquals(javaProcess.getAttribute(UsesJmx.JMX_PORT), 54321)
    }
}


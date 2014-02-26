package brooklyn.location.jclouds;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.Template;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.location.LocationSpec;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.Asserts;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.CompoundRuntimeException;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;

/**
 * @author Shane Witbeck
 */
public class JcloudsLocationTest implements JcloudsLocationConfig {

    // Don't care which image; not actually provisioning
    private static final String US_EAST_IMAGE_ID = "us-east-1/ami-7d7bfc14";
    
    public static final RuntimeException BAIL_OUT_FOR_TESTING = 
            new RuntimeException("early termination for test");
    
    public static class BailOutJcloudsLocation extends JcloudsLocation {
        public static final ConfigKey<Function<ConfigBag,Void>> BUILD_TEMPLATE_INTERCEPTOR = ConfigKeys.newConfigKey(new TypeToken<Function<ConfigBag,Void>>() {}, "buildtemplateinterceptor");
        
        ConfigBag lastConfigBag;

        public BailOutJcloudsLocation() {
            super();
        }

        public BailOutJcloudsLocation(Map<?, ?> conf) {
            super(conf);
        }

        @Override
        public Template buildTemplate(ComputeService computeService, ConfigBag config) {
            lastConfigBag = config;
            if (getConfig(BUILD_TEMPLATE_INTERCEPTOR) != null) getConfig(BUILD_TEMPLATE_INTERCEPTOR).apply(config);
            throw BAIL_OUT_FOR_TESTING;
        }
        protected void tryObtainAndCheck(Map<?,?> flags, Predicate<? super ConfigBag> test) {
            try {
                obtain(flags);
            } catch (Exception e) {
                if (e==BAIL_OUT_FOR_TESTING || e.getCause()==BAIL_OUT_FOR_TESTING 
                        || (e instanceof CompoundRuntimeException && ((CompoundRuntimeException)e).getAllCauses().contains(BAIL_OUT_FOR_TESTING))) {
                    test.apply(lastConfigBag);
                } else {
                    throw Exceptions.propagate(e);
                }
            }
        }
    }

    public static class CountingBailOutJcloudsLocation extends BailOutJcloudsLocation {
        int buildTemplateCount = 0;
        @Override
        public Template buildTemplate(ComputeService computeService, ConfigBag config) {
            buildTemplateCount++;
            return super.buildTemplate(computeService, config);
        }
    }
    
    public static class BailOutWithTemplateJcloudsLocation extends JcloudsLocation {
        ConfigBag lastConfigBag;
        Template template;

        public BailOutWithTemplateJcloudsLocation() {
            super();
        }

        public BailOutWithTemplateJcloudsLocation(Map<?, ?> conf) {
            super(conf);
        }

        @Override
        public Template buildTemplate(ComputeService computeService, ConfigBag config) {
            template = super.buildTemplate(computeService, config);

            lastConfigBag = config;
            throw BAIL_OUT_FOR_TESTING;
        }

        protected synchronized void tryObtainAndCheck(Map<?,?> flags, Predicate<ConfigBag> test) {
            try {
                obtain(flags);
            } catch (Throwable e) {
                if (e == BAIL_OUT_FOR_TESTING) {
                    test.apply(lastConfigBag);
                } else {
                    throw Exceptions.propagate(e);
                }
            }
        }
         
        public Template getTemplate() {
            return template;
        }
    }
    
    protected BailOutJcloudsLocation newSampleBailOutJcloudsLocationForTesting() {
        return newSampleBailOutJcloudsLocationForTesting(ImmutableMap.<ConfigKey<?>,Object>of());
    }
    
    protected BailOutJcloudsLocation newSampleBailOutJcloudsLocationForTesting(Map<?,?> config) {
        Map<ConfigKey<?>,?> allConfig = MutableMap.<ConfigKey<?>,Object>builder()
                .put(IMAGE_ID, "bogus")
                .put(CLOUD_PROVIDER, "aws-ec2")
                .put(ACCESS_IDENTITY, "bogus")
                .put(CLOUD_REGION_ID, "bogus")
                .put(ACCESS_CREDENTIAL, "bogus")
                .put(USER, "fred")
                .put(MIN_RAM, 16)
                .put(JcloudsLocation.MACHINE_CREATE_ATTEMPTS, 1)
                .putAll((Map)config)
                .build();
        return managementContext.getLocationManager().createLocation(LocationSpec.create(BailOutJcloudsLocation.class)
                .configure(allConfig));
    }
    
    protected BailOutWithTemplateJcloudsLocation newSampleBailOutWithTemplateJcloudsLocation() {
        return newSampleBailOutWithTemplateJcloudsLocation(ImmutableMap.<ConfigKey<?>,Object>of());
    }

    protected BailOutWithTemplateJcloudsLocation newSampleBailOutWithTemplateJcloudsLocation(Map<?,?> config) {
        String identity = (String) brooklynProperties.get("brooklyn.location.jclouds.aws-ec2.identity");
        if (identity == null) identity = (String) brooklynProperties.get("brooklyn.jclouds.aws-ec2.identity");
        String credential = (String) brooklynProperties.get("brooklyn.location.jclouds.aws-ec2.credential");
        if (credential == null) credential = (String) brooklynProperties.get("brooklyn.jclouds.aws-ec2.credential");
        
        Map<ConfigKey<?>,?> allConfig = MutableMap.<ConfigKey<?>,Object>builder()
                .put(CLOUD_PROVIDER, AbstractJcloudsTest.AWS_EC2_PROVIDER)
                .put(CLOUD_REGION_ID, AbstractJcloudsTest.AWS_EC2_USEAST_REGION_NAME)
                .put(IMAGE_ID, US_EAST_IMAGE_ID) // so it runs faster, without loading all EC2 images
                .put(ACCESS_IDENTITY, identity)
                .put(ACCESS_CREDENTIAL, credential)
                .put(USER, "fred")
                .put(INBOUND_PORTS, "[22, 80, 9999]")
                .putAll((Map)config)
                .build();
        
        return managementContext.getLocationManager().createLocation(LocationSpec.create(BailOutWithTemplateJcloudsLocation.class)
                .configure(allConfig));
    }

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
    
    private BrooklynProperties brooklynProperties;
    private LocalManagementContext managementContext;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContext();
        brooklynProperties = managementContext.getBrooklynProperties();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearUp() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testCreateWithFlagsDirectly() throws Exception {
        BailOutJcloudsLocation jcl = newSampleBailOutJcloudsLocationForTesting();
        jcl.tryObtainAndCheck(MutableMap.of(MIN_CORES, 2), checkerFor("fred", 16, 2));
    }

    @Test
    public void testCreateWithFlagsDirectlyAndOverride() throws Exception {
        BailOutJcloudsLocation jcl = newSampleBailOutJcloudsLocationForTesting();
        jcl.tryObtainAndCheck(MutableMap.of(MIN_CORES, 2, MIN_RAM, 8), checkerFor("fred", 8, 2));
    }

    @Test
    public void testCreateWithFlagsSubLocation() throws Exception {
        BailOutJcloudsLocation jcl = newSampleBailOutJcloudsLocationForTesting();
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
        Map<ConfigKey<?>, Object> flags = Maps.newHashMap();
        flags.put(IMAGE_ID, "bogus");
        flags.put(CLOUD_PROVIDER, "aws-ec2");
        flags.put(ACCESS_IDENTITY, "bogus");
        flags.put(CLOUD_REGION_ID, "bogus");
        flags.put(ACCESS_CREDENTIAL, "bogus");
        flags.put(USER, "fred");
        flags.put(MIN_RAM, 16);
        flags.put(MACHINE_CREATE_ATTEMPTS, 3);
        CountingBailOutJcloudsLocation jcl = managementContext.getLocationManager().createLocation(
                LocationSpec.create(CountingBailOutJcloudsLocation.class).configure(flags));
        jcl.tryObtainAndCheck(ImmutableMap.of(), Predicates.<ConfigBag>alwaysTrue());
        Assert.assertEquals(jcl.buildTemplateCount, 3);
    }

    @Test(groups={"Live", "Live-sanity"})
    public void testCreateWithInboundPorts() {
        BailOutWithTemplateJcloudsLocation jcloudsLocation = newSampleBailOutWithTemplateJcloudsLocation();
        jcloudsLocation = (BailOutWithTemplateJcloudsLocation) jcloudsLocation.newSubLocation(MutableMap.of());
        jcloudsLocation.tryObtainAndCheck(MutableMap.of(), templateCheckerFor("[22, 80, 9999]"));
        int[] ports = new int[] {22, 80, 9999};
        Assert.assertEquals(jcloudsLocation.template.getOptions().getInboundPorts(), ports);
    }
    
    @Test(groups={"Live", "Live-sanity"})
    public void testCreateWithInboundPortsOverride() {
        BailOutWithTemplateJcloudsLocation jcloudsLocation = newSampleBailOutWithTemplateJcloudsLocation();
        jcloudsLocation = (BailOutWithTemplateJcloudsLocation) jcloudsLocation.newSubLocation(MutableMap.of());
        jcloudsLocation.tryObtainAndCheck(MutableMap.of(INBOUND_PORTS, "[23, 81, 9998]"), templateCheckerFor("[23, 81, 9998]"));
        int[] ports = new int[] {23, 81, 9998};
        Assert.assertEquals(jcloudsLocation.template.getOptions().getInboundPorts(), ports);
    }

    @Test
    public void testCreateWithMaxConcurrentCallsUnboundedByDefault() throws Exception {
        final int numCalls = 20;
        ConcurrencyTracker interceptor = new ConcurrencyTracker();
        ExecutorService executor = Executors.newCachedThreadPool();

        try {
            final BailOutJcloudsLocation jcloudsLocation = newSampleBailOutJcloudsLocationForTesting(ImmutableMap.of(BailOutJcloudsLocation.BUILD_TEMPLATE_INTERCEPTOR, interceptor));
            
            for (int i = 0; i < numCalls; i++) {
                executor.execute(new Runnable() {
                    @Override public void run() {
                        jcloudsLocation.tryObtainAndCheck(MutableMap.of(), Predicates.alwaysTrue());
                    }});
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
            final BailOutJcloudsLocation jcloudsLocation = newSampleBailOutJcloudsLocationForTesting(ImmutableMap.of(
                    BailOutJcloudsLocation.BUILD_TEMPLATE_INTERCEPTOR, interceptor,
                    JcloudsLocation.MAX_CONCURRENT_MACHINE_CREATIONS, maxConcurrentCreations));
            
            for (int i = 0; i < numCalls; i++) {
                executor.execute(new Runnable() {
                    @Override public void run() {
                        jcloudsLocation.tryObtainAndCheck(MutableMap.of(), Predicates.alwaysTrue());
                    }});
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
            final BailOutJcloudsLocation jcloudsLocation = newSampleBailOutJcloudsLocationForTesting(ImmutableMap.of(
                    BailOutJcloudsLocation.BUILD_TEMPLATE_INTERCEPTOR, interceptor,
                    JcloudsLocation.MAX_CONCURRENT_MACHINE_CREATIONS, maxConcurrentCreations));
            
    
            for (int i = 0; i < numCalls; i++) {
                final BailOutJcloudsLocation subLocation = (BailOutJcloudsLocation) jcloudsLocation.newSubLocation(MutableMap.of());
                executor.execute(new Runnable() {
                    @Override public void run() {
                        subLocation.tryObtainAndCheck(MutableMap.of(), Predicates.alwaysTrue());
                    }});
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

    // TODO more tests, where flags come in from resolver, named locations, etc
}

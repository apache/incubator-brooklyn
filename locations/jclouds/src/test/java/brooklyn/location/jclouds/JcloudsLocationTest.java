package brooklyn.location.jclouds;

import java.util.Map;

import javax.annotation.Nullable;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.Template;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.Entities;
import brooklyn.location.LocationSpec;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Predicate;

/**
 * @author Shane Witbeck
 */
public class JcloudsLocationTest implements JcloudsLocationConfig {

    public static final RuntimeException BAIL_OUT_FOR_TESTING = 
            new RuntimeException("early termination for test");
    
    public static class BailOutJcloudsLocation extends JcloudsLocation {
       ConfigBag lastConfigBag;

       public BailOutJcloudsLocation() {
          super();
       }
       
       public BailOutJcloudsLocation(Map<?, ?> conf) {
            super(conf);
        }
        
        
        @Override
        protected Template buildTemplate(ComputeService computeService, ConfigBag config) {
            lastConfigBag = config;
            throw BAIL_OUT_FOR_TESTING;
        }
        protected synchronized void tryObtainAndCheck(Map<?,?> flags, Predicate<ConfigBag> test) {
            try {
                obtain(flags);
            } catch (Throwable e) {
                if (e==BAIL_OUT_FOR_TESTING) {
                    test.apply(lastConfigBag);
                } else {
                    throw Exceptions.propagate(e);
                }
            }
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
         protected Template buildTemplate(ComputeService computeService, ConfigBag config) {
             template = super.buildTemplate(computeService, config);
             
             lastConfigBag = config;
             throw BAIL_OUT_FOR_TESTING;
         }
         protected synchronized void tryObtainAndCheck(Map<?,?> flags, Predicate<ConfigBag> test) {
             try {
                 obtain(flags);
             } catch (Throwable e) {
                 if (e==BAIL_OUT_FOR_TESTING) {
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
        return managementContext.getLocationManager().createLocation(LocationSpec.create(BailOutJcloudsLocation.class)
                .configure(MutableMap.of(
                        IMAGE_ID, "bogus",
                        CLOUD_PROVIDER, "aws-ec2",
                        ACCESS_IDENTITY, "bogus",
                        CLOUD_REGION_ID, "bogus",
                        ACCESS_CREDENTIAL, "bogus",
                        USER, "fred",
                        MIN_RAM, 16)));
    }
    
    protected BailOutWithTemplateJcloudsLocation newSampleBailOutWithTemplateJcloudsLocation() {
        String identity = (String) brooklynProperties.get("brooklyn.location.jclouds.aws-ec2.identity");
        if (identity == null) identity = (String) brooklynProperties.get("brooklyn.jclouds.aws-ec2.identity");
        String credential = (String) brooklynProperties.get("brooklyn.location.jclouds.aws-ec2.credential");
        if (credential == null) identity = (String) brooklynProperties.get("brooklyn.jclouds.aws-ec2.credential");
        
        return managementContext.getLocationManager().createLocation(LocationSpec.create(BailOutWithTemplateJcloudsLocation.class)
                .configure(MutableMap.of(
                        CLOUD_PROVIDER, "aws-ec2",
                        CLOUD_REGION_ID, "eu-west-1",
                        IMAGE_ID, "us-east-1/ami-7d7bfc14", // so it runs faster, without loading all EC2 images
                        ACCESS_IDENTITY, identity,
                        ACCESS_CREDENTIAL, credential,
                        USER, "fred",
                        INBOUND_PORTS, "[22, 80, 9999]")));
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
        brooklynProperties = BrooklynProperties.Factory.newDefault();
        managementContext = new LocalManagementContext(brooklynProperties);
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
    
    @Test(groups="Live")
    public void testCreateWithInboundPorts() {
        BailOutWithTemplateJcloudsLocation jcloudsLocation = newSampleBailOutWithTemplateJcloudsLocation();
        jcloudsLocation = (BailOutWithTemplateJcloudsLocation) jcloudsLocation.newSubLocation(MutableMap.of());
        jcloudsLocation.tryObtainAndCheck(MutableMap.of(), templateCheckerFor("[22, 80, 9999]"));
        int[] ports = new int[] {22, 80, 9999};
        Assert.assertEquals(jcloudsLocation.template.getOptions().getInboundPorts(), ports);
    }
    
    @Test(groups="Live")
    public void testCreateWithInboundPortsOverride() {
        BailOutWithTemplateJcloudsLocation jcloudsLocation = newSampleBailOutWithTemplateJcloudsLocation();
        jcloudsLocation = (BailOutWithTemplateJcloudsLocation) jcloudsLocation.newSubLocation(MutableMap.of());
        jcloudsLocation.tryObtainAndCheck(MutableMap.of(INBOUND_PORTS, "[23, 81, 9998]"), templateCheckerFor("[23, 81, 9998]"));
        int[] ports = new int[] {23, 81, 9998};
        Assert.assertEquals(jcloudsLocation.template.getOptions().getInboundPorts(), ports);
    }

    // TODO more tests, where flags come in from resolver, named locations, etc
}

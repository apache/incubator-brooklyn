package brooklyn.location.jclouds.pool;

import java.util.Arrays;

import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.Entities;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.management.internal.LocalManagementContext;

import com.google.common.collect.ImmutableSet;

public class JcloudsMachinePoolLiveTest {

    public static final Logger log = LoggerFactory.getLogger(JcloudsMachinePoolLiveTest.class);
    
    public static class SamplePool extends MachinePool {
        public SamplePool(ComputeService svc) {
            super(svc);
        }

        public final static ReusableMachineTemplate 
            USUAL_VM = 
                new ReusableMachineTemplate("usual").templateOwnedByMe().
                tagOptional("tagForUsualVm").
                metadataOptional("metadataForUsualVm", "12345").
                minRam(1024).minCores(2);

        public final static ReusableMachineTemplate 
            ANYONE_NOT_TINY_VM = 
                new ReusableMachineTemplate("anyone").
                minRam(512).minCores(1).strict(false);

        public static final ReusableMachineTemplate 
            VM_LARGE1 = 
                new ReusableMachineTemplate("vm.large1").templateOwnedByMe().
                minRam(16384).minCores(4),
            VM_SMALL1 = 
                new ReusableMachineTemplate("vm.small1").templateOwnedByMe().smallest();
        
        { registerTemplates(USUAL_VM, ANYONE_NOT_TINY_VM, VM_LARGE1, VM_SMALL1); }
    }
    
    public static String getRequiredSystemProperty(String field) {
        String result = System.getProperty(field);
        if (result==null)
            throw new IllegalArgumentException("This requires 'field' to be passed on the command-line (-D"+field+"=...");
        return result;
    }

    private LocalManagementContext managementContext;
    private JcloudsLocation jcloudsLocation;
    private ComputeServiceContext context;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        // Don't let any defaults from brooklyn.properties (except credentials) interfere with test
        BrooklynProperties brooklynProperties = BrooklynProperties.Factory.newDefault();
        for (String key : ImmutableSet.copyOf(brooklynProperties.asMapWithStringKeys().keySet())) {
            if (key.startsWith("brooklyn.jclouds") && !(key.endsWith("identity") || key.endsWith("credential"))) {
                brooklynProperties.remove(key);
            }
            
            // Also removes scriptHeader (e.g. if doing `. ~/.bashrc` and `. ~/.profile`, then that can cause "stdin: is not a tty")
            if (key.startsWith("brooklyn.ssh")) {
                brooklynProperties.remove(key);
            }
        }
        
        managementContext = new LocalManagementContext(brooklynProperties);
        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve("aws-ec2:eu-west-1");
        
        context = ContextBuilder.newBuilder("aws-ec2")
                .modules(Arrays.asList(new SshjSshClientModule(), new SLF4JLoggingModule()))
                .credentials(jcloudsLocation.getIdentity(), jcloudsLocation.getCredential())
                .build(ComputeServiceContext.class);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
        if (context != null) context.close();
    }
    
    @Test(groups={"Live","WIP"})
    public void buildClaimAndDestroy() {
        ComputeService svc = context.getComputeService();
        SamplePool p = new SamplePool(svc);
        log.info("buildClaimAndDestroy: created pool");
        p.refresh();
        log.info("buildClaimAndDestroy: refreshed pool");
        p.ensureExists(2, SamplePool.USUAL_VM);
        log.info("buildClaimAndDestroy: ensure have 2");
        MachineSet l = p.claim(1, SamplePool.USUAL_VM);
        Assert.assertEquals(l.size(), 1);
        log.info("buildClaimAndDestroy: claimed 1");
        MachineSet unclaimedUsual = p.unclaimed(MachinePoolPredicates.matching(SamplePool.USUAL_VM));
        log.info("buildClaimAndDestroy: unclaimed now "+unclaimedUsual);
        Assert.assertTrue(!unclaimedUsual.isEmpty());
        p.destroy(unclaimedUsual);
        unclaimedUsual = p.unclaimed(MachinePoolPredicates.matching(SamplePool.USUAL_VM));
        log.info("buildClaimAndDestroy: destroyed, unclaimed now "+unclaimedUsual);
        log.info("end");
    }
    

    
}

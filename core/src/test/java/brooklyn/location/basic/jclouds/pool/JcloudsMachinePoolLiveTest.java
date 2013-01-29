package brooklyn.location.basic.jclouds.pool;

import java.util.Arrays;

import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

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

    @Test(groups="Live")
    public void buildClaimAndDestroy() {
        ComputeServiceContext context = ContextBuilder.newBuilder("aws-ec2")
                .modules(Arrays.asList(new SshjSshClientModule(), new SLF4JLoggingModule()))
                .credentials(getRequiredSystemProperty("identity"), getRequiredSystemProperty("credential"))
                .build(ComputeServiceContext.class);
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

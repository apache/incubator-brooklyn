package brooklyn.location.jclouds

import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

import brooklyn.entity.basic.Entities
import brooklyn.location.basic.SshMachineLocation
import brooklyn.management.ManagementContext
import brooklyn.util.internal.ssh.sshj.SshjTool

import com.google.common.collect.ImmutableMap
import com.google.common.collect.Maps

public abstract class AbstractJcloudsLocationTest {
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractJcloudsLocationTest.class)

    private final String provider

    protected JcloudsLocation loc;
    protected Collection<SshMachineLocation> machines = []
    protected ManagementContext ctx;

    protected AbstractJcloudsLocationTest(String provider) {
        this.provider = provider
    }

    /**
     * The location and image id tuplets to test.
     */
    @DataProvider(name = "fromImageId")
    public abstract Object[][] cloudAndImageIds();

    /**
     * A single location and image id tuplet to test.
     */
    @DataProvider(name = "fromFirstImageId")
    public Object[][] cloudAndImageFirstId() {
        Object[][] all = cloudAndImageIds();
        return (all ? [all[0]] : []);
    }

    /**
     * The location and image name pattern tuplets to test.
     */
    @DataProvider(name = "fromImageNamePattern")
    public abstract Object[][] cloudAndImageNamePatterns();

    /**
     * The location, image pattern and image owner tuplets to test.
     */
    @DataProvider(name = "fromImageDescriptionPattern")
    public abstract Object[][] cloudAndImageDescriptionPatterns();

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        ctx = Entities.newManagementContext(ImmutableMap.of("provider", provider));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        List<Exception> exceptions = []
        machines.each {
            try {
                loc?.release(it)
            } catch (Exception e) {
                LOG.warn("Error releasing machine $it; continuing...", e)
                exceptions.add(e)
            }
        }
        if (exceptions) {
            throw exceptions.get(0)
        }
        machines.clear()
        
        if (ctx != null) Entities.destroyAll(ctx);
    }

    @Test(dataProvider="fromImageId")
    public void testTagMapping(String regionName, String imageId, String imageOwner) {
        loc = ctx.getLocationRegistry().resolve(provider + (regionName == null ? "" : ":" + regionName), [ identity:"DUMMY", credential:"DUMMY" ])
        Map tagMapping = [ imageId:imageId, imageOwner:imageOwner, ]
        loc.setTagMapping([MyEntityType:tagMapping])

        Map flags = loc.getProvisioningFlags(["MyEntityType"])
        assertTrue(Maps.difference(flags, tagMapping).entriesOnlyOnRight().isEmpty(), "flags="+flags);
    }

    @Test(groups = [ "Live" ], dataProvider="fromImageId")
    public void testProvisionVmUsingImageId(String regionName, String imageId, String imageOwner) {
        loc = ctx.getLocationRegistry().resolve(provider + (regionName == null ? "" : ":" + regionName));
        SshMachineLocation machine = obtainMachine([imageId:imageId, imageOwner:imageOwner])

        LOG.info("Provisioned vm $machine; checking if ssh'able")
        assertTrue machine.isSshable()
    }
    
    @Test(groups = [ "Live" ], dataProvider="fromImageNamePattern")
    public void testProvisionVmUsingImageNamePattern(String regionName, String imageNamePattern, String imageOwner) {
        loc = ctx.getLocationRegistry().resolve(provider + (regionName == null ? "" : ":" + regionName));
        SshMachineLocation machine = obtainMachine([imageNamePattern:imageNamePattern, imageOwner:imageOwner])
        
        LOG.info("Provisioned AWS vm $machine; checking if ssh'able")
        assertTrue machine.isSshable()
    }
    
    @Test(groups = "Live", dataProvider="fromImageDescriptionPattern")
    public void testProvisionVmUsingImageDescriptionPattern(String regionName, String imageDescriptionPattern, String imageOwner) {
        loc = ctx.getLocationRegistry().resolve(provider + (regionName == null ? "" : ":" + regionName));
        SshMachineLocation machine = obtainMachine([imageDescriptionPattern:imageDescriptionPattern, imageOwner:imageOwner])
        
        LOG.info("Provisioned AWS vm $machine; checking if ssh'able")
        assertTrue machine.isSshable()
    }

    // FIXME Fails: can't ssh to machine using `myname`
    // FIXME Do we really want to hard-code ssh key paths here?
    @Test(groups = ["Live","WIP"], dataProvider="fromFirstImageId")
    public void testProvisioningVmWithCustomUsername(String regionName, String imageId, String imageOwner) {
        loc = ctx.getLocationRegistry().resolve(provider + (regionName == null ? "" : ":" + regionName));
        Map flags = [
            imageId:imageId,
            imageOwner:imageOwner,
            userName:"myname"
        ]

        SshMachineLocation machine = obtainMachine(flags)
        LOG.info("Provisioned vm $machine; checking if ssh'able")

        def t = new SshjTool(user:"myname", host:machine.address.getHostName(), publicKeyFile:sshpublicKey.getAbsolutePath(), privateKeyFile:sshPrivateKey.getAbsolutePath())
        t.connect()
        t.execCommands([ "date" ])
        t.disconnect()

        assertTrue machine.isSshable()
    }

    // Use this utility method to ensure machines are released on tearDown
    protected SshMachineLocation obtainMachine(Map flags) {
        SshMachineLocation result = loc.obtain(flags)
        machines.add(result)
        return result
    }
    
    protected SshMachineLocation release(SshMachineLocation machine) {
        machines.remove(machine)
        loc.release(machine)
    }
}

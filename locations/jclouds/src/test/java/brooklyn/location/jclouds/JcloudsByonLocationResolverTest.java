package brooklyn.location.jclouds;

import static org.testng.Assert.assertEquals;

import java.util.Set;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.location.LocationRegistry;
import brooklyn.location.basic.BasicLocationRegistry;
import brooklyn.location.basic.FixedListMachineProvisioningLocation;
import brooklyn.management.internal.LocalManagementContext;

import com.google.common.collect.Iterables;

public class JcloudsByonLocationResolverTest {

    private BrooklynProperties brooklynProperties;
    private LocalManagementContext managementContext;
    private LocationRegistry registry;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        brooklynProperties = BrooklynProperties.Factory.newDefault();
        managementContext = new LocalManagementContext(brooklynProperties);
        registry = new BasicLocationRegistry(managementContext);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) managementContext.terminate();
    }

    // TODO Requires that a VM already exists; could create that VM first to make test more robust
    @Test(groups={"Live","WIP"})
    public void testResolvesJclouds() throws Exception {
        String user = "aled";
        String instanceId = "i-f2014593";
        String ip = "54.226.119.72";
        String hostname = "ec2-54-226-119-72.compute-1.amazonaws.com";
        
        String spec = "jcloudsByon:(provider=\"aws-ec2\",region=\"us=east-1\",user=\""+user+"\",hosts=\""+instanceId+"\")";
        FixedListMachineProvisioningLocation<JcloudsSshMachineLocation> loc = resolve(spec);
        
        Set<JcloudsSshMachineLocation> machines = loc.getAllMachines();
        JcloudsSshMachineLocation machine = Iterables.getOnlyElement(machines);
        assertEquals(machine.getParent().getProvider(), "aws-ec2");
        assertEquals(machine.getAddress().getHostAddress(), ip);
        assertEquals(machine.getAddress().getHostName(), hostname);
        assertEquals(machine.getUser(), user);
    }

    @SuppressWarnings("unchecked")
    private FixedListMachineProvisioningLocation<JcloudsSshMachineLocation> resolve(String spec) {
        return (FixedListMachineProvisioningLocation<JcloudsSshMachineLocation>) registry.resolve(spec);
    }
}

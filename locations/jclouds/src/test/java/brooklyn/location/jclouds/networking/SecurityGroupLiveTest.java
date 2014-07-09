package brooklyn.location.jclouds.networking;

import org.testng.annotations.Test;

@Test(groups = {"Live", "WIP"})
public class SecurityGroupLiveTest {

    public void testCreateEc2WithSecurityGroup() {
        SecurityGroupDefinition sgDef = new SecurityGroupDefinition()
            .allowingInternalPorts(8097, 8098).allowingInternalPortRange(6000, 7999)
            .allowingPublicPort(8099);
        // TODO create machine and test
    }
}

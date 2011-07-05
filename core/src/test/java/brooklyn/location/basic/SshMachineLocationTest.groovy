package brooklyn.location.basic

import static org.testng.Assert.*

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.location.PortRange

/**
 * Test the {@link SshMachineLocation} implementation of the {@link brooklyn.location.Location} interface.
 */
public class SshMachineLocationTest {
    private SshMachineLocation host;
    
    @BeforeMethod
    public void setUp() throws Exception {
        host = new SshMachineLocation(address: InetAddress.getLocalHost());
    }

    // Note: requires `ssh localhost` to be setup such that no password is required    
    @Test(groups = "Integration")
    public void testSshRun() throws Exception {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        String expectedName = System.getProperty("user.name")
        host.run(out : outStream, "whoami; exit");
        def outString = new String(outStream.toByteArray())
        
        assertTrue outString.contains(expectedName), outString
    }
    
    @Test
    public void obtainSpecificPortGivesOutPortOnlyOnce() {
        int port = 2345
        assertTrue host.obtainSpecificPort(port)
        assertFalse host.obtainSpecificPort(port)
        host.releasePort(port)
        assertTrue host.obtainSpecificPort(port)
    }
    
    @Test
    public void obtainPortInRangeGivesBackRequiredPortOnlyIfAvailable() {
        int port = 2345
        assertEquals host.obtainPort(new BasicPortRange(port, port)), port
        assertEquals host.obtainPort(new BasicPortRange(port, port)), -1
        host.releasePort(port)
        assertEquals host.obtainPort(new BasicPortRange(port, port)), port
    }
    
    @Test
    public void obtainPortInWideRange() {
        int lowerPort = 2345
        int upperPort = 2350
        PortRange range = new BasicPortRange(lowerPort, upperPort)
        for (int i = lowerPort; i <= upperPort; i++) {
            assertEquals host.obtainPort(range), i
        }
        assertEquals host.obtainPort(range), -1
        
        host.releasePort(lowerPort)
        assertEquals host.obtainPort(range), lowerPort
        assertEquals host.obtainPort(range), -1
    }
}

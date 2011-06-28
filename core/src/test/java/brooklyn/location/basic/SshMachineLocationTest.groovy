package brooklyn.location.basic

import static org.testng.Assert.*

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.location.basic.SshMachineLocation;

/**
 * Test the {@link SshMachineLocation} implementation of the {@link Location} interface.
 */
public class SshMachineLocationTest {
    private SshMachineLocation host;
    
    @BeforeMethod(groups = "Integration")
    public void setUp() throws Exception {
        host = new SshMachineLocation();
        host.host = "localhost"
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
}

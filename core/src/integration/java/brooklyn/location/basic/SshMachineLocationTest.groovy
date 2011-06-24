package brooklyn.location.basic

import org.junit.Assert
import org.junit.Before
import org.junit.Test

class SshMachineLocationTest {

    private SshMachineLocation host;
    
    @Before
    public void setUp() throws Exception {
        host = new SshMachineLocation();
        host.host = "localhost"
    }

    // Note: requires `ssh localhost` to be setup such that no password is required    
    @Test
    public void testSshRun() throws Exception {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        String expectedName = System.getProperty("user.name")
        host.run(out : outStream, "whoami; exit");
        def outString = new String(outStream.toByteArray())
        
        Assert.assertTrue(outString, outString.contains(expectedName))
    }
}

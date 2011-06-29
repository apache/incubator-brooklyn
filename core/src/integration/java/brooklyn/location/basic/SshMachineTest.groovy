package brooklyn.location.basic

import org.testng.annotations.BeforeTest
import org.testng.annotations.Test;

class SshMachineTest {

    private SshMachine host;
    
    @BeforeTest
    public void setUp() throws Exception {
        host = new SshMachine(InetAddress.getLocalHost());
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

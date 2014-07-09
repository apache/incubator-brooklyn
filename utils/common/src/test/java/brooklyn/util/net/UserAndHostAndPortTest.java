package brooklyn.util.net;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import com.google.common.net.HostAndPort;

public class UserAndHostAndPortTest {

    @Test
    public void testFromParts() throws Exception {
        assertIt(UserAndHostAndPort.fromParts("myuser", "myhost", 1234), "myuser", HostAndPort.fromParts("myhost", 1234));
    }
    
    @Test
    public void testFromString() throws Exception {
        assertIt(UserAndHostAndPort.fromString("myuser@myhost:1234"), "myuser", HostAndPort.fromParts("myhost", 1234));
        assertIt(UserAndHostAndPort.fromString("myuser @ myhost:1234"), "myuser", HostAndPort.fromParts("myhost", 1234));
        assertIt(UserAndHostAndPort.fromString("myuser @ myhost"), "myuser", HostAndPort.fromString("myhost"));
    }
    
    private void assertIt(UserAndHostAndPort actual, String user, HostAndPort hostAndPort) {
        assertEquals(actual.getUser(), user);
        assertEquals(actual.getHostAndPort(), hostAndPort);
        if (hostAndPort.hasPort()) {
            assertEquals(actual.toString(), user + "@" + hostAndPort.getHostText() + ":" + hostAndPort.getPort());
        } else {
            assertEquals(actual.toString(), user + "@" + hostAndPort.getHostText());
        }
    }
}

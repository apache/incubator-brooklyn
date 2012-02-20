package brooklyn.util;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.net.ServerSocket;

import org.testng.annotations.Test;

public class NetworkUtilsTest {

    @Test
    public void testIsPortAvailableReportsTrueWhenPortIsFree() throws Exception {
        int port = 58768;
        for (int i = 0; i < 10; i++) {
            assertTrue(NetworkUtils.isPortAvailable(port));
        }
    }
    
    @Test
    public void testIsPortAvailableReportsFalseWhenPortIsInUse() throws Exception {
        int port = 58768;
        ServerSocket ss = null;
        try {
            ss = new ServerSocket(port);
            assertFalse(NetworkUtils.isPortAvailable(port));
        } finally {
            if (ss != null) {
                ss.close();
            }
        }

        assertTrue(NetworkUtils.isPortAvailable(port));
    }
}

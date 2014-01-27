package brooklyn.location.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.internal.ssh.sshj.SshjTool;

/**
 * Tests the re-use of SshTools in SshMachineLocation
 */
public class SshMachineLocationReuseIntegrationTest {

    public static class RecordingSshjTool extends SshjTool {
        static int connectionCount = 0;

        public RecordingSshjTool(Map<String, ?> map) {
            super(map);
        }

        @Override
        public void connect() {
            connectionCount += 1;
            super.connect();
        }

        public static void reset() {
            RecordingSshjTool.connectionCount = 0;
        }
    }

    private SshMachineLocation host;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        host = new SshMachineLocation(MutableMap.of(
                "address", InetAddress.getLocalHost(),
                SshTool.PROP_TOOL_CLASS, RecordingSshjTool.class.getName()));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (host != null) Closeables.closeQuietly(host);
        RecordingSshjTool.reset();
    }

    @Test(groups = "Integration")
    public void testBasicReuse() throws Exception {
        host.execScript("mysummary", ImmutableList.of("exit"));
        host.execScript("mysummary", ImmutableList.of("exit"));
        assertEquals(RecordingSshjTool.connectionCount, 1, "Expected one SSH connection to have been recorded");
    }

    @Test(groups = "Integration")
    public void testReuseWithInterestingProps() throws Exception {
        host.execScript(customSshConfigKeys(), "mysummary", ImmutableList.of("exit"));
        host.execScript(customSshConfigKeys(), "mysummary", ImmutableList.of("exit"));
        assertEquals(RecordingSshjTool.connectionCount, 1, "Expected one SSH connection to have been recorded");
    }

    @Test(groups = "Integration")
    public void testNewConnectionForDifferentProps() throws Exception {
        host.execScript("mysummary", ImmutableList.of("exit"));
        host.execScript(customSshConfigKeys(), "mysummary", ImmutableList.of("exit"));
        assertEquals(RecordingSshjTool.connectionCount, 2, "Expected two SSH connections to have been recorded");
    }

    @Test(groups = "Integration")
    public void testSshToolReusedWhenConfigDiffers() throws Exception {
        Map<String, Object> props = customSshConfigKeys();
        host.execScript(props, "mysummary", ImmutableList.of("exit"));

        // Use another output stream for second request
        props.put(SshTool.PROP_SCRIPT_HEADER.getName(), "#!/bin/bash -e\n");
        host.execScript(props, "mysummary", ImmutableList.of("exit"));
        assertEquals(RecordingSshjTool.connectionCount, 1, "Expected one SSH connection to have been recorded even though out script header differed.");
    }

    public Map<String, Object> customSshConfigKeys() throws UnknownHostException {
        return MutableMap.<String, Object>of(
                "address", InetAddress.getLocalHost(),
                SshTool.PROP_SESSION_TIMEOUT.getName(), 20000,
                SshTool.PROP_CONNECT_TIMEOUT.getName(), 50000,
                SshTool.PROP_SCRIPT_HEADER.getName(), "#!/bin/bash");
    }
}

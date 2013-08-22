package brooklyn.util.internal.ssh.sshj;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.schmizz.sshj.connection.channel.direct.Session;

import org.testng.annotations.Test;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.internal.ssh.SshException;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.internal.ssh.SshToolAbstractIntegrationTest;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Test the operation of the {@link SshJschTool} utility class.
 */
public class SshjToolIntegrationTest extends SshToolAbstractIntegrationTest {

    @Override
    protected SshTool newTool(Map<String,?> flags) {
        return new SshjTool(flags);
    }

    // TODO requires vt100 terminal emulation to work?
    @Test(enabled = false, groups = {"Integration"})
    public void testExecShellWithCommandTakingStdin() throws Exception {
        // Uses `tee` to redirect stdin to the given file; cntr-d (i.e. char 4) stops tee with exit code 0
        String content = "blah blah";
        String out = execShellDirectWithTerminalEmulation("tee "+remoteFilePath, content, ""+(char)4, "echo file contents: `cat "+remoteFilePath+"`");

        assertTrue(out.contains("file contents: blah blah"), "out="+out);
    }

    @Test(groups = {"Integration"})
    public void testGivesUpAfterMaxRetries() throws Exception {
        final AtomicInteger callCount = new AtomicInteger();
        
        final SshTool localtool = new SshjTool(ImmutableMap.of("sshTries", 3, "host", "localhost", "privateKeyFile", "~/.ssh/id_rsa")) {
            protected SshAction<Session> newSessionAction() {
                callCount.incrementAndGet();
                throw new RuntimeException("Simulating ssh execution failure");
            }
        };
        
        tools.add(localtool);
        try {
            localtool.execScript(ImmutableMap.<String,Object>of(), ImmutableList.of("true"));
            fail();
        } catch (SshException e) {
            if (!e.toString().contains("out of retries")) throw e;
            assertEquals(callCount.get(), 3);
        }
    }

    @Test(groups = {"Integration"})
    public void testReturnsOnSuccessWhenRetrying() throws Exception {
        final AtomicInteger callCount = new AtomicInteger();
        final int successOnAttempt = 2;
        final SshTool localtool = new SshjTool(ImmutableMap.of("sshTries", 3, "host", "localhost", "privateKeyFile", "~/.ssh/id_rsa")) {
            protected SshAction<Session> newSessionAction() {
                callCount.incrementAndGet();
                if (callCount.incrementAndGet() >= successOnAttempt) {
                    return super.newSessionAction();
                } else {
                    throw new RuntimeException("Simulating ssh execution failure");
                }
            }
        };
        
        tools.add(localtool);
        localtool.execScript(ImmutableMap.<String,Object>of(), ImmutableList.of("true"));
        assertEquals(callCount.get(), successOnAttempt);
    }

    @Test(groups = {"Integration"})
    public void testGivesUpAfterMaxTime() throws Exception {
        final AtomicInteger callCount = new AtomicInteger();
        final SshTool localtool = new SshjTool(ImmutableMap.of("sshTriesTimeout", 1000, "host", "localhost", "privateKeyFile", "~/.ssh/id_rsa")) {
            protected SshAction<Session> newSessionAction() {
                callCount.incrementAndGet();
                try {
                    Thread.sleep(600);
                } catch (InterruptedException e) {
                    throw Exceptions.propagate(e);
                }
                throw new RuntimeException("Simulating ssh execution failure");
            }
        };
        
        tools.add(localtool);
        try {
            localtool.execScript(ImmutableMap.<String,Object>of(), ImmutableList.of("true"));
            fail();
        } catch (SshException e) {
            if (!e.toString().contains("out of time")) throw e;
            assertEquals(callCount.get(), 2);
        }
    }
    
    private String execShellDirect(List<String> cmds) {
        return execShellDirect(cmds, ImmutableMap.<String,Object>of());
    }
    
    private String execShellDirect(List<String> cmds, Map<String,?> env) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exitcode = ((SshjTool)tool).execShellDirect(ImmutableMap.of("out", out), cmds, env);
        String outstr = new String(out.toByteArray());
        assertEquals(exitcode, 0, outstr);
        return outstr;
    }

    private String execShellDirectWithTerminalEmulation(String... cmds) {
        return execShellDirectWithTerminalEmulation(Arrays.asList(cmds));
    }
    
    private String execShellDirectWithTerminalEmulation(List<String> cmds) {
        return execShellDirectWithTerminalEmulation(cmds, ImmutableMap.<String,Object>of());
    }
    
    private String execShellDirectWithTerminalEmulation(List<String> cmds, Map<String,?> env) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exitcode = ((SshjTool)tool).execShellDirect(ImmutableMap.of("allocatePTY", true, "out", out), cmds, env);
        String outstr = new String(out.toByteArray());
        assertEquals(exitcode, 0, outstr);
        return outstr;
    }
}

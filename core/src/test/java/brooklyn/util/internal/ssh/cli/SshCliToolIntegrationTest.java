package brooklyn.util.internal.ssh.cli;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.Map;

import org.testng.annotations.Test;

import brooklyn.util.MutableMap;
import brooklyn.util.internal.ssh.SshException;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.internal.ssh.SshToolIntegrationTest;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Test the operation of the {@link SshJschTool} utility class.
 */
public class SshCliToolIntegrationTest extends SshToolIntegrationTest {

    @Override
    protected SshTool newSshTool(Map<String,?> flags) {
        return new SshCliTool(flags);
    }

    // Need to have at least one test method here (rather than just inherited) for eclipse to recognize it
    @Test(enabled = false)
    public void testDummy() throws Exception {
    }
    
    // Doing .connect() isn't enough; need to cause ssh or scp to be invoked
    @Test(groups = {"Integration"})
    public void testConnectWithInvalidUserThrowsException() throws Exception {
        final SshTool localtool = newSshTool(ImmutableMap.of("user", "wronguser", "host", "localhost", "privateKeyFile", "~/.ssh/id_rsa"));
        tools.add(localtool);
        try {
            localtool.connect();
            int result = localtool.execScript(ImmutableMap.<String,Object>of(), ImmutableList.of("date"));
            fail("exitCode="+result+", but expected exception");
        } catch (SshException e) {
            if (!e.toString().contains("failed to connect")) throw e;
        }
    }
    
    // Setting last modified date not yet supported for cli-based ssh
    @Override
    @Test(enabled=false, groups = {"Integration"})
    public void testCreateFileWithLastModifiedDate() throws Exception {
        super.testCreateFileWithLastModifiedDate();
    }
    
    @Test(groups = {"Integration"})
    public void testCreateFileFromBytes() throws Exception {
        super.testCreateFileFromBytes();
    }
    
    @Test(groups = {"Integration"})
    public void testExecShellReturningZeroExitCode() throws Exception {
        super.testExecShellReturningZeroExitCode();
    }
}

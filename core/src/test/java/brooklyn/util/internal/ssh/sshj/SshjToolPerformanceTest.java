package brooklyn.util.internal.ssh.sshj;

import java.util.Map;

import org.testng.annotations.Test;

import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.internal.ssh.SshToolAbstractPerformanceTest;

/**
 * Test the performance of different variants of invoking the sshj tool.
 * 
 * Intended for human-invocation and inspection, to see which parts are most expensive.
 */
public class SshjToolPerformanceTest extends SshToolAbstractPerformanceTest {

    @Override
    protected SshTool newSshTool(Map<String,?> flags) {
        return new SshjTool(flags);
    }
    
    // Need to have at least one test method here (rather than just inherited) for eclipse to recognize it
    @Test(enabled = false)
    public void testDummy() throws Exception {
    }
}

package brooklyn.util.internal.ssh.process;

import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import brooklyn.util.internal.ssh.ShellToolAbstractTest;

/**
 * Test the operation of the {@link ProcessTool} utility class.
 */
public class ProcessToolIntegrationTest extends ShellToolAbstractTest {

    @Override
    protected ProcessTool newTool() {
        return new ProcessTool();
    }

    // ones here included as *non*-integration tests. must run on windows and linux.
    // (also includes integration tests from parent)

    @Test
    public void testPortableCommand() throws Exception {
        String out = execScript("echo hello world");
        assertTrue(out.contains("hello world"), "out="+out);
    }

}

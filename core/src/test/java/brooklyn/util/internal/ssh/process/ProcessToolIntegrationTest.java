package brooklyn.util.internal.ssh.process;

import static org.testng.Assert.assertTrue;

import java.util.Arrays;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.config.ConfigBag;
import brooklyn.util.internal.ssh.ShellToolAbstractTest;

/**
 * Test the operation of the {@link ProcessTool} utility class.
 */
public class ProcessToolIntegrationTest extends ShellToolAbstractTest {

    @Override
    protected ProcessTool newUnregisteredTool(Map<String,?> flags) {
        return new ProcessTool(flags);
    }

    // ones here included as *non*-integration tests. must run on windows and linux.
    // (also includes integration tests from parent)

    @Test
    public void testPortableCommand() throws Exception {
        String out = execScript("echo hello world");
        assertTrue(out.contains("hello world"), "out="+out);
    }

    @Test(groups="Integration")
    public void testLoginShell() {
        // this detection scheme only works for commands; can't test whether it works for scripts without 
        // requiring stuff in bash_profile / profile / etc, which gets hard to make portable;
        // it is nearly the same code path on the impl so this is probably enough 
        
        final String LOGIN_SHELL_CHECK = "shopt -q login_shell && echo 'yes, login shell' || echo 'no, not login shell'";
        ConfigBag config = ConfigBag.newInstance().configure(ProcessTool.PROP_NO_EXTRA_OUTPUT, true);
        String out;
        
        out = execCommands(config, Arrays.asList(LOGIN_SHELL_CHECK), null);
        Assert.assertEquals(out.trim(), "no, not login shell", "out = "+out);
        
        config.configure(ProcessTool.PROP_LOGIN_SHELL, true);
        out = execCommands(config, Arrays.asList(LOGIN_SHELL_CHECK), null);
        Assert.assertEquals(out.trim(), "yes, login shell", "out = "+out);
    }

}

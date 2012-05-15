package brooklyn.cli;

import java.io.InputStreamReader;

import org.testng.annotations.Test;

public class CliTest {
    @Test(enabled = false, groups = "Integration")
    public void testLaunchCliHelp() throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command("brooklyn.sh", "help");
        Process brooklyn = pb.start();
        InputStreamReader reader = new InputStreamReader(brooklyn.getErrorStream());
        // TODO etc ...
    }

    @Test(enabled = false, groups = "Integration")
    public void testLaunchCliApp() throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command("brooklyn.sh", "--verbose", "launch", "--app", "brooklyn.cli.TestApp", "--location", "localhost", "--noConsole");
        Process brooklyn = pb.start();
    }

    @Test(enabled = false, groups = "Integration")
    public void testLaunchCliAppError() throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command("brooklyn.sh", "launch", "--doesNotExist", "nothing");
        Process brooklyn = pb.start();
    }
}

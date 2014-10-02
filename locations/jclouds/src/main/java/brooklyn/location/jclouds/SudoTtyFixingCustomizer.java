package brooklyn.location.jclouds;

import org.jclouds.compute.ComputeService;

import com.google.common.annotations.Beta;

import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.ssh.SshTasks;
import brooklyn.util.task.ssh.SshTasks.OnFailingTask;

/**
 * Wraps Brooklyn's sudo-tty mitigations in a {@link JcloudsLocationCustomizer} for easy(-ish) consumption
 * in YAML blueprints:
 *
 * <pre>
 *   name: My App
 *   brooklyn.config:
 *     provisioning.properties:
 *       customizerType: brooklyn.location.jclouds.SudoTtyFixingCustomizer
 *   services: ...
 * </pre>
 *
 * <p>This class should be seen as a temporary workaround and might disappear completely if/when Brooklyn takes care of this automatically.
 *
 * <p>See
 * <a href='http://unix.stackexchange.com/questions/122616/why-do-i-need-a-tty-to-run-sudo-if-i-can-sudo-without-a-password'>http://unix.stackexchange.com/questions/122616/why-do-i-need-a-tty-to-run-sudo-if-i-can-sudo-without-a-password</a>
 * for background.
 */
@Beta
public class SudoTtyFixingCustomizer extends BasicJcloudsLocationCustomizer {

    @Override
    public void customize(JcloudsLocation location, ComputeService computeService, JcloudsSshMachineLocation machine) {
        DynamicTasks.queueIfPossible(SshTasks.dontRequireTtyForSudo(machine, OnFailingTask.FAIL)).orSubmitAndBlock();
        DynamicTasks.waitForLast();
    }

}

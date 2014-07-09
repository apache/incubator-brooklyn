package brooklyn.util.file;

import java.util.Map;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.TaskAdaptable;
import brooklyn.management.TaskFactory;
import brooklyn.util.ResourceUtils;
import brooklyn.util.net.Urls;
import brooklyn.util.task.Tasks;

public class ArchiveTasks {

    /** as {@link #deploy(ResourceUtils, Map, String, SshMachineLocation, String, String, String)} with the most common parameters */
    public static TaskFactory<?> deploy(final ResourceUtils optionalResolver, final String archiveUrl, final SshMachineLocation machine, final String destDir) {
        return deploy(optionalResolver, null, archiveUrl, machine, destDir, false, null, null);
    }
    
    /** returns a task which installs and unpacks the given archive, as per {@link ArchiveUtils#deploy(ResourceUtils, Map, String, SshMachineLocation, String, String, String)} */
    public static TaskFactory<?> deploy(final ResourceUtils resolver, final Map<String, ?> props, final String archiveUrl, final SshMachineLocation machine, final String destDir, final boolean keepArchiveAfterDeploy, final String tmpDir, final String destFile) {
        return new TaskFactory<TaskAdaptable<?>>() {
            @Override
            public TaskAdaptable<?> newTask() {
                return Tasks.<Void>builder().name("deploying "+Urls.getBasename(archiveUrl)).description("installing "+archiveUrl+" and unpacking to "+destDir).body(new Runnable() {
                    @Override
                    public void run() {
                        ArchiveUtils.deploy(resolver, props, archiveUrl, machine, destDir, keepArchiveAfterDeploy, tmpDir, destFile);
                    }
                }).build();
            }
        };
    }
    
}

package brooklyn.entity.salt;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import brooklyn.demo.CumulusRDFApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.java.JarBuilder;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.ssh.SshPutTaskFactory;
import brooklyn.util.task.ssh.SshTasks;
import brooklyn.util.text.Strings;
import brooklyn.util.text.TemplateProcessor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public class SaltStackMasterSshDriver extends JavaSoftwareProcessSshDriver implements SaltStackMasterDriver {

    public SaltStackMasterSshDriver(SaltStackMasterImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public SaltStackMasterImpl getEntity() {
        return (SaltStackMasterImpl) super.getEntity();
    }

    @Override
    protected String getLogFileLocation() {
        return "master.log";
    }

    private String getPidFile() {
        return "master.pid";
    }

    @Override
    public void install() {
        String url = Entities.getRequiredUrlConfig(getEntity(), SaltStackMaster.BOOTSTRAP_URL);
        copyTemplate(url, "/etc/salt/master");

        // Copy the file contents to the remote machine
//        DynamicTasks.queue(SshEffectorTasks.put("/tmp/cumulus.yaml").contents(contents)).get();

        // Run Salt bootstrap task to install master
        DynamicTasks.queue(SaltTasks.installSaltMaster(getEntity(), getRunDir(), true));


        newScript("createInstallDir")
                .body.append("mkdir -p "+getInstallDir())
                .failOnNonZeroResultCode()
                .execute();

        newScript(INSTALLING).
                failOnNonZeroResultCode().
                body.append("").execute();
    }

    @Override
    public void customize() {
    }

    @Override
    public void launch() {
        newScript(ImmutableMap.of("usePidFile", false), LAUNCHING)
                .body.append(BashCommands.sudo("start salt-master"))
                .execute();
    }

    @Override
    public boolean isRunning() {
        return newScript(ImmutableMap.of("usePidFile", false), CHECK_RUNNING)
                .body.append(BashCommands.sudo("status salt-master"))
                .execute() == 0;
    }

    @Override
    public void stop() {
        newScript(ImmutableMap.of("usePidFile", false), STOPPING)
                .body.append(BashCommands.sudo("stop salt-master"))
                .execute();
    }
}

package brooklyn.entity.monitoring.monit;

import static java.lang.String.format;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.lifecycle.ScriptHelper;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.ssh.BashCommands;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class MonitSshDriver extends AbstractSoftwareProcessSshDriver implements MonitDriver {
    
    private static final Logger LOG = LoggerFactory.getLogger(MonitSshDriver.class);

    private String expandedInstallDir;
    private String remoteControlFilePath;
    
    public MonitSshDriver(MonitNodeImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }
    @Override
    public void install() {
        DownloadResolver resolver = ((EntityInternal)entity).getManagementContext().getEntityDownloadsManager().newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        expandedInstallDir = getInstallDir() + "/" + resolver.getUnpackedDirectoryName(format("monit-%s", getVersion()));
        List<String> commands = ImmutableList.<String>builder()
            .add(BashCommands.INSTALL_TAR)
            .add(BashCommands.INSTALL_CURL)
            .add(BashCommands.commandToDownloadUrlsAs(urls, saveAs))
            .add(format("tar xfvz %s", saveAs))
            .build();
        
        newScript(INSTALLING)
            .failOnNonZeroResultCode()
            .body
            .append(commands)
            .execute();
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING)
            .body.append("echo copying control file")
            .execute();  //create the directory
        String controlFileUrl = getEntity().getConfig(MonitNode.CONTROL_FILE_URL);
        remoteControlFilePath = getRunDir() + "/monit.monitrc";
        copyTemplate(controlFileUrl, remoteControlFilePath);
        // Monit demands the control file has permissions <= 0700
        newScript(CUSTOMIZING)
            .body.append("chmod 600 " + remoteControlFilePath)
            .execute();
    }

    @Override
    public void launch() {
        String args = Joiner.on(" ").join(
            "-c", remoteControlFilePath,
            "-p", getMonitPidFile(),
            "-l", getMonitLogFile()
            );
        String command = format("touch %s && %s/bin/monit %s > out.log 2> err.log < /dev/null", getMonitPidFile(),
            expandedInstallDir, args);
        newScript(LAUNCHING)
            .updateTaskAndFailOnNonZeroResultCode()
            .body.append(command)
            .execute();
    }
    
    @Override
    public boolean isRunning() {
        Map flags = ImmutableMap.of("usePidFile", getMonitPidFile());
        return newScript(flags, CHECK_RUNNING).execute() == 0;
    }
    
    @Override
    public void stop() {
        new ScriptHelper(this, "Send SIGTERM to Monit process")
            .body.append("kill -s SIGTERM `cat " + getMonitPidFile() + "`")
            .execute();        
    }
    
    protected String getMonitPidFile() {
        // Monit seems to dislike starting with a relative path to a pid file.
        return getRunDir() + "/monit.pid";
    }
    
    public String getMonitLogFile() {
        return getRunDir() + "/monit.log";
    }

    public String getOsTag() {
        OsDetails os = getLocation().getOsDetails();
        if (os == null) {
            // Default to 64 bit linux
            return "linux-x64";
        } else if (os.isMac()) {
            return "macosx-universal";
        } else {
            String arch = os.is64bit() ? "x64" : "x86";
            return "linux-" + arch;
        }
    }
    
    @Override
    public String getStatusCmd() {
        return format("%s/bin/monit -c %s status", expandedInstallDir, remoteControlFilePath);
    }
}

package brooklyn.entity.database.crate;

import static java.lang.String.format;

import java.util.List;

import brooklyn.util.collections.MutableMap;
import com.google.common.collect.ImmutableList;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;

public class CrateNodeSshDriver extends JavaSoftwareProcessSshDriver {

    public CrateNodeSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
        setExpandedInstallDir(Os.mergePaths(getInstallDir(),
                resolver.getUnpackedDirectoryName(format("crate-%s", getVersion()))));
    }

    @Override
    public void install() {
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();

        List<String> commands = ImmutableList.<String>builder()
                .addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs))
                .add ("tar xvfz "+saveAs)
                .build();

        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands).execute();
    }

    @Override
    public void customize() {

    }

    @Override
    public void launch() {
        StringBuilder command = new StringBuilder(getExpandedInstallDir())
                .append("/bin/crate >").append(getLogFileLocation())
                .append(" 2> err.log < /dev/null")
                .append(" -d")
                .append(" -p ").append(getPidFileLocation());
        newScript(LAUNCHING)
                .failOnNonZeroResultCode()
                .body.append(command).execute();

    }

    @Override
    public boolean isRunning() {
        return newScript (MutableMap.of("usePidFile", getPidFileLocation()), CHECK_RUNNING)
                .execute() == 0;
    }

    @Override
    public void stop() {
        newScript (MutableMap.of("usePidFile", getPidFileLocation()), STOPPING)
                .execute();

    }

    @Override
    protected String getLogFileLocation() {
        return getRunDir() + "/server.log";
    }

    protected String getPidFileLocation () {
        return getRunDir() + "/pid.txt";
    }
}

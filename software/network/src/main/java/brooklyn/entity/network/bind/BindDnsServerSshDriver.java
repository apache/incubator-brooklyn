package brooklyn.entity.network.bind;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.NetworkUtils;
import brooklyn.util.ssh.CommonCommands;

import com.google.common.collect.ImmutableList;

public class BindDnsServerSshDriver extends JavaSoftwareProcessSshDriver implements BindDnsServerDriver {

    protected String expandedInstallDir;

    public BindDnsServerSshDriver(BindDnsServerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public BindDnsServerImpl getEntity() {
        return (BindDnsServerImpl) super.getEntity();
    }

    @Override
    protected String getLogFileLocation() {
        return "/var/named/data/named.run";
    }

    protected String getExpandedInstallDir() {
        if (expandedInstallDir == null) throw new IllegalStateException("expandedInstallDir is null; most likely install was not called");
        return expandedInstallDir;
    }

    @Override
    public void install() {
        DownloadResolver resolver = Entities.newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        expandedInstallDir = getInstallDir()+"/"+resolver.getUnpackedDirectoryName(String.format("apache-karaf-%s", getVersion()));

        List<String> commands = ImmutableList.<String>builder()
                .addAll(CommonCommands.downloadUrlAs(urls, saveAs))
                .add(CommonCommands.INSTALL_TAR)
                .add("tar xzfv " + saveAs)
                .build();

        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands)
                .execute();
    }

    @Override
    public void customize() {
        Map<String, Object> ports = new HashMap<String, Object>();
        ports.put("dnsPort", 53);

        NetworkUtils.checkPortsValid(ports);
        newScript(CUSTOMIZING).
                body.append(String.format("cd %s", getRunDir())).execute();
    }

    @Override
    public void launch() {
        Map<String, Object> flags = new HashMap<String, Object>();
        flags.put("usePidFile", true);

        newScript(flags, LAUNCHING).
                body.append("").execute();
    }

    @Override
    public boolean isRunning() {
        return newScript(CHECK_RUNNING).
                    body.append("").execute() == 0;
    }

    @Override
    public void stop() {
        newScript(STOPPING).
                body.append("")
        ).execute();
    }

}

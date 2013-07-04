package brooklyn.entity.network.bind;

import java.util.List;
import java.util.Map;

import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.NetworkUtils;
import brooklyn.util.collections.MutableMap;
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

    @Override
    public void install() {
        List<String> commands = ImmutableList.<String>builder()
                .add(CommonCommands.installPackage(MutableMap.of("yum", "bind"), "bind"))
                .add("which setenforce && " + CommonCommands.sudo("setenforce 0"))
                .build();

        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands)
                .execute();
    }

    @Override
    public void customize() {
        Integer dnsPort = getEntity().getDnsPort();
        Map<String, Object> ports = MutableMap.<String, Object>of("dnsPort", dnsPort);
        NetworkUtils.checkPortsValid(ports);
        // XXX do we need to grab another public IP somewhere???
        newScript(CUSTOMIZING)
                .body.append(
                        CommonCommands.sudo("iptables -I INPUT 1 -p udp -m state --state NEW --dport " + dnsPort + " -j ACCEPT"),
                        CommonCommands.sudo("iptables -I INPUT 1 -p tcp -m state --state NEW --dport " + dnsPort + " -j ACCEPT")
                ).execute();
    }

    @Override
    public void launch() {
        newScript(MutableMap.of("usePidFile", false), LAUNCHING).
        body.append(CommonCommands.sudo("service named start")).execute();
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of("usePidFile", false), CHECK_RUNNING)
                    .body.append(CommonCommands.sudo("service named status")).execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of("usePidFile", false), STOPPING)
                .body.append(CommonCommands.sudo("service named stop")).execute();
    }

}

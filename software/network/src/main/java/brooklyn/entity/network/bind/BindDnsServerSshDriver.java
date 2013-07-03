package brooklyn.entity.network.bind;

import java.util.HashMap;
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
        List<String> commands = ImmutableList.of(CommonCommands.installPackage(MutableMap.of("yum", "bind"), "bind"));

        newScript(MutableMap.of("allocatePTY", "true"), INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands)
                .execute();
    }

    @Override
    public void customize() {
        Map<String, Object> ports = new HashMap<String, Object>();
        ports.put("dnsPort", entity.getAttribute(BindDnsServer.DNS_PORT));
        NetworkUtils.checkPortsValid(ports);
        // XXX do we need to grab another public IP somewhere???
        newScript(MutableMap.of("allocatePTY", "true"), CUSTOMIZING)
                .body.append(
                        CommonCommands.sudo("iptables -A INPUT -p udp -m state --state NEW --dport 53 -j ACCEPT"),
                        CommonCommands.sudo("iptables -A INPUT -p tcp -m state --state NEW --dport 53 -j ACCEPT"),
                        CommonCommands.sudo("service iptables restart")
                    ).execute();
    }

    @Override
    public void launch() {
        newScript(MutableMap.of("allocatePTY", "true", "usePidFile", "false"), LAUNCHING).
        body.append(CommonCommands.sudo("service named start")).execute();
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of("allocatePTY", "true", "usePidFile", "false"), CHECK_RUNNING).
                    body.append(CommonCommands.sudo("service named status")).execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of("allocatePTY", "true", "usePidFile", "false"), STOPPING).
        body.append(CommonCommands.sudo("service named stop")).execute();
    }

}

package brooklyn.entity.messaging.rabbit;

import static brooklyn.util.ssh.BashCommands.installPackage;
import static java.lang.String.format;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.messaging.amqp.AmqpServer;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.ssh.BashCommands;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * TODO javadoc
 */
public class RabbitSshDriver extends AbstractSoftwareProcessSshDriver implements RabbitDriver {

    private static final Logger log = LoggerFactory.getLogger(RabbitSshDriver.class);
    
    private String expandedInstallDir;

    public RabbitSshDriver(RabbitBrokerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    protected String getLogFileLocation() { return getRunDir()+"/"+entity.getId()+".log"; }

    public Integer getAmqpPort() { return entity.getAttribute(AmqpServer.AMQP_PORT); }

    public String getVirtualHost() { return entity.getAttribute(AmqpServer.VIRTUAL_HOST_NAME); }

    public String getErlangVersion() { return entity.getConfig(RabbitBroker.ERLANG_VERSION); }

    @Override
    public RabbitBrokerImpl getEntity() {
        return (RabbitBrokerImpl) super.getEntity();
    }
    
    private String getExpandedInstallDir() {
        if (expandedInstallDir == null) throw new IllegalStateException("expandedInstallDir is null; most likely install was not called");
        return expandedInstallDir;
    }
    
    @Override
    public void install() {
        DownloadResolver resolver = entity.getManagementContext().getEntityDownloadsManager().newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        expandedInstallDir = getInstallDir()+"/"+resolver.getUnpackedDirectoryName(format("rabbitmq_server-%s", getVersion()));
        
        List<String> commands = ImmutableList.<String>builder()
                .add(installPackage(// NOTE only 'port' states the version of Erlang used, maybe remove this constraint?
                        ImmutableMap.of(
                                "apt", "erlang-nox erlang-dev",
                                "port", "erlang@"+getErlangVersion()+"+ssl"),
                        "erlang"))
                .addAll(BashCommands.downloadUrlAs(urls, saveAs))
                .add(BashCommands.installExecutable("tar"))
                .add(format("tar xvzf %s",saveAs))
                .build();

        newScript(INSTALLING).
                failOnNonZeroResultCode().
                body.append(commands).execute();
    }

    @Override
    public void customize() {
        Networking.checkPortsValid(MutableMap.of("amqpPort", getAmqpPort()));
        newScript(CUSTOMIZING)
                .body.append(
                    format("cp -R %s/* .", getExpandedInstallDir())
                )
                .execute();
    }

    @Override
    public void launch() {
        newScript(MutableMap.of("usePidFile", false), LAUNCHING)
            .body.append(
                "nohup ./sbin/rabbitmq-server > console-out.log 2> console-err.log &",
                "for i in {1..10}\n" +
                    "do\n" +
                     "    grep 'broker running' console-out.log && exit\n" +
                     "    sleep 1\n" +
                     "done",
                "echo \"Couldn't determine if rabbitmq-server is running\"",
                "exit 1"
            ).execute();
    }

    @Override
    public void configure() {
        newScript(CUSTOMIZING)
            .body.append(
                "./sbin/rabbitmqctl add_vhost "+getEntity().getVirtualHost(),
                "./sbin/rabbitmqctl set_permissions -p "+getEntity().getVirtualHost()+" guest \".*\" \".*\" \".*\""
            ).execute();
    }

    public String getPidFile() { return "rabbitmq.pid"; }
    
    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of("usePidFile", false), CHECK_RUNNING)
                .body.append("./sbin/rabbitmqctl -q status")
                .execute() == 0;
    }


    @Override
    public void stop() {
        newScript(MutableMap.of("usePidFile", false), STOPPING)
                .body.append("./sbin/rabbitmqctl stop")
                .execute();
    }

    @Override
    public void kill() {
        stop(); // TODO No pid file to easily do `kill -9`
    }

    @Override
    public Map<String, String> getShellEnvironment() {
        return MutableMap.<String, String>builder()
                .putAll(super.getShellEnvironment())
                .put("RABBITMQ_HOME", getRunDir())
                .put("RABBITMQ_LOG_BASE", getRunDir())
                .put("RABBITMQ_NODENAME", getEntity().getId())
                .put("RABBITMQ_NODE_PORT", getAmqpPort().toString())
                .put("RABBITMQ_PID_FILE", getRunDir()+"/"+getPidFile())
                .build();
    }
}

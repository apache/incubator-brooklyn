package brooklyn.entity.messaging.rabbit;

import static brooklyn.entity.basic.lifecycle.CommonCommands.*

import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.lifecycle.StartStopSshDriver
import brooklyn.entity.messaging.amqp.AmqpServer
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.NetworkUtils

/**
 * TODO javadoc
 */
public class RabbitSshDriver extends StartStopSshDriver {

    private static final Logger log = LoggerFactory.getLogger(RabbitSshDriver.class);

    public RabbitSshDriver(RabbitBroker entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    @Override
    protected String getLogFileLocation() { "${runDir}/${entity.id}.log"; }

    public Integer getAmqpPort() { entity.getAttribute(AmqpServer.AMQP_PORT) }

    public String getVirtualHost() { entity.getAttribute(AmqpServer.VIRTUAL_HOST_NAME) }

    public String getErlangVersion() { entity.getConfig(RabbitBroker.ERLANG_VERSION) }
    
    /*
     * NOTE although this works, better off making it simple and doing things with tgz and standard packages in the
     * central os repository. plus, using the custom extensions to the script helper is not really useful, as for any
     * complex work we should be recommending a chef recipie or similar instead. move this to another branch with just
     * extended commands and mention on brooklyn-dev, see whether it is something people are actually interested in?
     */

    @Override
    public void install() {
        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(
                    INSTALL_WGET,
                    on("\\(Red Hat\\|Fedora\\|CentOS\\|SUSE\\)",
                        chain([
                            "wget -O /etc/yum.repos.d/epel-erlang.repo http://repos.fedorapeople.org/repos/peter/erlang/epel-erlang.repo",
                            "rpm -Uvh http://download.fedoraproject.org/pub/epel/6/i386/epel-release-6-5.noarch.rpm",
                            "rpm --import http://www.rabbitmq.com/rabbitmq-signing-key-public.asc"
                        ])
                    ),
                    on("\\(Red Hat\\|Fedora\\|CentOS\\)",
                        "wget -O rabbitmq-server-${version}-1.rpm http://www.rabbitmq.com/releases/rabbitmq-server/v${version}/rabbitmq-server-${version}-1.noarch.rpm"
                    ),
                    on("SUSE", "wget -O rabbitmq-server-${version}-1.rpm http://www.rabbitmq.com/releases/rabbitmq-server/v${version}/rabbitmq-server-${version}-1.suse.noarch.rpm"),
                    on("Ubuntu", "wget http://www.rabbitmq.com/releases/rabbitmq-server/v${version}/rabbitmq-server_${version}-1_all.deb"),
                    installPackage("erlang", // NOTE only 'port' states the version of Erlang used, maybe remove this constraint?
                            apt:"erlang-nox erlang-dev",
                            port:"erlang@${erlangVersion}+ssl"),
                    installPackage("rabbitmq-server",
                            deb:"rabbitmq-server_${version}-1_all.deb",
                            apt:"rabbitmq-server=${version}-1",
                            rpm:"rabbitmq-server-${version}-1.rpm",
                            port:"rabbitmq-server@${version}"),
                    ok(sudo("rabbitmqctl -q stop > /dev/null 2>&1"))
                )
                .execute()
    }

    @Override
    public void customize() {
        NetworkUtils.checkPortsValid(amqpPort:amqpPort)
        newScript(CUSTOMIZING)
                .body.append(sudo("chown -R rabbitmq ${runDir}"))
                .execute()
    }

    @Override
    public void launch() {
        newScript(LAUNCHING, usePidFile:false)
                .body.append(sudo("nohup rabbitmq-server -detached > /dev/null 2>&1"))
                .execute()
    }

    public String getPidFile() { "rabbitmq.pid" }
    
    @Override
    public boolean isRunning() {
        newScript(CHECK_RUNNING, usePidFile:false)
                .body.append(sudo("rabbitmqctl -q status"))
                .execute() == 0;
    }


    @Override
    public void stop() {
        newScript(STOPPING, usePidFile:false)
                .body.append(sudo("rabbitmqctl stop"))
                .execute()
    }

    public Map<String, String> getShellEnvironment() {
        Map result = super.getShellEnvironment()
        result << [
            RABBITMQ_LOG_BASE: "${runDir}",
            RABBITMQ_NODENAME: "${entity.id}",
            RABBITMQ_NODE_PORT: "${amqpPort}",
            RABBITMQ_PID_FILE: "${runDir}/${pidFile}"
        ]
    }
}

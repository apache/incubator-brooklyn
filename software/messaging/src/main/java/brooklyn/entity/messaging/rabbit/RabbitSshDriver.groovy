/*
 * TODO license
 */
package brooklyn.entity.messaging.rabbit;

import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.lifecycle.CommonCommands
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
    
    @Override
    public void install() {
        newScript(INSTALLING).
                failOnNonZeroResultCode().
                body.append(
                    CommonCommands.installPackage("erlang", apt:"erlang-nox erlang-dev", port:"erlang@${erlangVersion}+ssl"),
                    CommonCommands.installPackage("rabbitmq-server", port:"rabbitmq-server@${version}")
                ).execute()
    }

    @Override
    public void customize() {
        NetworkUtils.checkPortsValid(amqpPort:amqpPort);
    }

    @Override
    public void launch() {
        newScript(LAUNCHING, usePidFile:false).
                body.append(
                    CommonCommands.sudo("chown -R rabbitmq ${runDir}"),
                    CommonCommands.sudo("nohup rabbitmq-server -detached")
                ).execute()
    }

    public String getPidFile() { "rabbitmq.pid" }
    
    @Override
    public boolean isRunning() {
        newScript(CHECK_RUNNING, usePidFile:false).
                body.append(
                    CommonCommands.sudo("rabbitmqctl -q status")
                ).execute() == 0;
    }


    @Override
    public void stop() {
        newScript(STOPPING, usePidFile:false).
                body.append(
                    CommonCommands.sudo("rabbitmqctl stop")
                ).execute();
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

package brooklyn.entity.messaging.rabbit;

import brooklyn.entity.messaging.Queue;
import brooklyn.event.feed.ssh.SshFeed;
import brooklyn.event.feed.ssh.SshPollConfig;
import brooklyn.event.feed.ssh.SshPollValue;

import com.google.common.base.Function;
import com.google.common.base.Functions;

public class RabbitQueue extends RabbitDestination implements Queue {

    private SshFeed sshFeed;

    public RabbitQueue() {
    }
    
    public String getName() {
        return getDisplayName();
    }

    @Override
    public void create() {
        setAttribute(QUEUE_NAME, getName());
        super.create();
    }

    @Override
    protected void connectSensors() {
        String runDir = getParent().getRunDir();
        String cmd = String.format("%s/sbin/rabbitmqctl list_queues -p /%s  | grep '%s'", runDir, getVirtualHost(), getQueueName());
        
        sshFeed = SshFeed.builder()
                .entity(this)
                .machine(machine)
                .poll(new SshPollConfig<Integer>(QUEUE_DEPTH_BYTES)
                        .env(shellEnvironment)
                        .command(cmd)
                        .onFailure(Functions.constant(-1))
                        .onSuccess(new Function<SshPollValue, Integer>() {
                                @Override public Integer apply(SshPollValue input) {
                                    return 0; // TODO parse out queue depth from output
                                }}))
                .poll(new SshPollConfig<Integer>(QUEUE_DEPTH_MESSAGES)
                        .env(shellEnvironment)
                        .command(cmd)
                        .onFailure(Functions.constant(-1))
                        .onSuccess(new Function<SshPollValue, Integer>() {
                                @Override public Integer apply(SshPollValue input) {
                                    return 0; // TODO parse out queue depth from output
                                }}))
                .build();
    }
    
    @Override
    protected void disconnectSensors() {
        if (sshFeed != null) sshFeed.stop();
        super.disconnectSensors();
    }
    
    /**
     * Return the AMQP name for the queue.
     */
    public String getQueueName() {
        return getName();
    }
}

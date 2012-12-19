package brooklyn.entity.messaging.rabbit;

import brooklyn.entity.Entity
import brooklyn.entity.messaging.Queue
import brooklyn.event.adapter.SshSensorAdapter

public class RabbitQueue extends RabbitDestination implements Queue {

    public RabbitQueue(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    public String getName() { getDisplayName() }

    @Override
    public void create() {
        setAttribute QUEUE_NAME, name
        super.create()
    }

    public void connectSensors() {
        def queueAdapter = sshAdapter.command("${owner.driver.runDir}/sbin/rabbitmqctl list_queues -p /${virtualHost}  | grep '${queueName}'")
        queueAdapter.poll(QUEUE_DEPTH_BYTES) {
            if (it == null || exitStatus != 0) return -1
            return 0 // TODO parse out queue depth from output
        }
        queueAdapter.poll(QUEUE_DEPTH_MESSAGES) {
            if (it == null || exitStatus != 0) return -1
            return 0 // TODO parse out queue depth from output
        }
    }

    /**
     * Return the AMQP name for the queue.
     */
    public String getQueueName() { name }
}

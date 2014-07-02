package brooklyn.entity.messaging.kafka;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.Callable;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.test.Asserts;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;

public class KafkaLiveTest extends AbstractEc2LiveTest {

    /**
     * Test that can install, start and use a Kafka cluster with two brokers.
     */
    @Override
    protected void doTest(Location loc) throws Exception {
        final KafkaCluster cluster = app.createAndManageChild(EntitySpec.create(KafkaCluster.class)
                .configure("startTimeout", 300) // 5 minutes
                .configure("initialSize", 2));
        app.start(ImmutableList.of(loc));

        Asserts.succeedsEventually(MutableMap.of("timeout", 300000l), new Callable<Void>() {
            @Override
            public Void call() {
                assertTrue(cluster.getAttribute(Startable.SERVICE_UP));
                assertTrue(cluster.getZooKeeper().getAttribute(Startable.SERVICE_UP));
                assertEquals(cluster.getCurrentSize().intValue(), 2);
                return null;
            }
        });

        Entities.dumpInfo(cluster);

        KafkaSupport support = new KafkaSupport(cluster);

        support.sendMessage("brooklyn", "TEST_MESSAGE");
        String message = support.getMessage("brooklyn");
        assertEquals(message, "TEST_MESSAGE");
    }

}

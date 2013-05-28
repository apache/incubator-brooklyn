package brooklyn.entity.nosql.mongodb;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mongodb.DBObject;
import groovy.time.TimeDuration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.concurrent.Callable;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class MongoDBReplicaSetIntegrationTest {

    private TestApplication app;
    private Collection<LocalhostMachineProvisioningLocation> localhostMachineProvisioningLocation;

    // Replica sets can take a while to start
    private static final TimeDuration TIMEOUT = new TimeDuration(0, 0, 180, 0);

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        LocalhostMachineProvisioningLocation location = new LocalhostMachineProvisioningLocation();
        localhostMachineProvisioningLocation = ImmutableList.of(location);
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app);
    }

    /**
     * Creates and starts a replica set, asserts it reaches the given size
     * and that the primary and secondaries are non-null.
     */
    private MongoDBReplicaSet makeAndStartReplicaSet(final Integer size, String testDescription) {
        // Sets secondaryPreferred so we can read from slaves.
        final MongoDBReplicaSet replicaSet = app.createAndManageChild(EntitySpecs.spec(MongoDBReplicaSet.class)
                .configure(DynamicCluster.INITIAL_SIZE, size)
                .configure("replicaSetName", "test-rs-"+testDescription)
                .configure("memberSpec", EntitySpecs.spec(MongoDBServer.class)
                        .configure("mongodbConfTemplateUrl", "classpath:///test-mongodb.conf")
                        .configure("port", "27017+")));
        app.start(localhostMachineProvisioningLocation);

        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT), new Callable<Boolean>() {
            @Override
            public Boolean call() {
                assertEquals(replicaSet.getCurrentSize(), size);
                assertNotNull(replicaSet.getPrimary());
                assertEquals(replicaSet.getSecondaries().size(), size-1);
                return true;
            }
        });
        return replicaSet;
    }

    @Test(groups = "Integration")
    public void testCanStartAndStopAReplicaSet() {
        final MongoDBReplicaSet replicaSet = makeAndStartReplicaSet(3, "can-start-and-stop");
        for (MongoDBServer s : replicaSet.getSecondaries()) {
            System.err.println(s);
        }
    }

    @Test(groups = "Integration", dependsOnMethods = { "testCanStartAndStopAReplicaSet" })
    public void testWriteToMasterAndReadFromSecondary() throws Exception {
        final MongoDBReplicaSet replicaSet = makeAndStartReplicaSet(3, "master-write-secondary-read");

        // Test we can read a document written to the primary from all secondaries
        final String documentId = MongoDBTestHelper.insert(replicaSet.getPrimary(), "meaning-of-life", 42);
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT), new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                assertEquals(replicaSet.getCurrentSize().intValue(), 3);
                for (MongoDBServer secondary : replicaSet.getSecondaries()) {
                    DBObject docOut = MongoDBTestHelper.getById(secondary, documentId);
                    assertEquals(docOut.get("meaning-of-life"), 42);
                }
                return true;
            }
        });
    }

    @Test(groups = "Integration", dependsOnMethods = { "testCanStartAndStopAReplicaSet" })
    public void testCanResizeAndReadFromNewInstances() throws Exception {
        final MongoDBReplicaSet replicaSet = makeAndStartReplicaSet(3, "resize-and-read-from-secondaries");

        // Test we can a document written to the primary from all secondaries
        final String documentId = MongoDBTestHelper.insert(replicaSet.getPrimary(), "meaning-of-life", 42);
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT), new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                assertEquals(replicaSet.getCurrentSize().intValue(), 3);
                for (MongoDBServer secondary : replicaSet.getSecondaries()) {
                    DBObject docOut = MongoDBTestHelper.getById(secondary, documentId);
                    assertEquals(docOut.get("meaning-of-life"), 42);
                }
                return true;
            }
        });

        // Resize and confirm new members get data
        replicaSet.resize(5);
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT), new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                assertEquals(replicaSet.getCurrentSize().intValue(), 5);
                Collection<MongoDBServer> secondaries = replicaSet.getSecondaries();
                assertEquals(secondaries.size(), 4);
                for (MongoDBServer secondary : secondaries) {
                    DBObject docOut = MongoDBTestHelper.getById(secondary, documentId);
                    assertEquals(docOut.get("meaning-of-life"), 42);
                }
                return true;
            }
        });

    }

    @Test(groups = "Integration", dependsOnMethods = { "testCanStartAndStopAReplicaSet" })
    public void testResizeToEvenNumberOfMembersIgnored() {
        final MongoDBReplicaSet replicaSet = makeAndStartReplicaSet(3, "resize-even-ignored");
        replicaSet.resize(4);
        TimeDuration thirtySeconds = new TimeDuration(0, 0, 30, 0);
        Asserts.succeedsContinually(ImmutableMap.of("timeout", thirtySeconds), new Runnable() {
            @Override
            public void run() {
                assertEquals(replicaSet.getCurrentSize().intValue(), 3);
            }
        });
    }

}

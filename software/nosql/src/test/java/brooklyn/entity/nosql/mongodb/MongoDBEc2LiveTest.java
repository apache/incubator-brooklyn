package brooklyn.entity.nosql.mongodb;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;
import com.google.common.collect.ImmutableList;
import com.mongodb.DBObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class MongoDBEc2LiveTest extends AbstractEc2LiveTest {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(MongoDBEc2LiveTest.class);

    @Override
    protected void doTest(Location loc) throws Exception {
        MongoDBServer entity = app.createAndManageChild(EntitySpec.create(MongoDBServer.class)
                .configure("mongodbConfTemplateUrl", "classpath:///test-mongodb.conf"));
        app.start(ImmutableList.of(loc));

        EntityTestUtils.assertAttributeEqualsEventually(entity, MongoDBServer.SERVICE_UP, true);

        String id = MongoDBTestHelper.insert(entity, "hello", "world!");
        DBObject docOut = MongoDBTestHelper.getById(entity, id);
        assertEquals(docOut.get("hello"), "world!");
    }

    @Test(enabled=false)
    public void testDummy() {} // Convince TestNG IDE integration that this really does have test methods

}

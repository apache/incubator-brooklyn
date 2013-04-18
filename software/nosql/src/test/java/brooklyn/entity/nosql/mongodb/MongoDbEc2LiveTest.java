package brooklyn.entity.nosql.mongodb;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;
import com.google.common.collect.ImmutableList;
import com.mongodb.DBObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class MongoDbEc2LiveTest extends AbstractEc2LiveTest {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(MongoDbEc2LiveTest.class);

    @Override
    protected void doTest(Location loc) throws Exception {
        MongoDbServer entity = app.createAndManageChild(EntitySpecs.spec(MongoDbServer.class)
                .configure("mongodbConfTemplateUrl", "classpath:///test-mongodb.conf"));
        app.start(ImmutableList.of(loc));

        EntityTestUtils.assertAttributeEqualsEventually(entity, MongoDbServer.SERVICE_UP, true);

        String id = MongoDbTestHelper.insert(entity, "hello", "world!");
        DBObject docOut = MongoDbTestHelper.getById(entity, id);
        assertEquals(docOut.get("hello"), "world!");
    }

    @Test(enabled=false)
    public void testDummy() {} // Convince TestNG IDE integration that this really does have test methods

}

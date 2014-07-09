package brooklyn.entity.database.mysql;

import org.testng.annotations.Test;

import brooklyn.entity.AbstractGoogleComputeLiveTest;
import brooklyn.entity.database.DatastoreMixins.DatastoreCommon;
import brooklyn.entity.database.VogellaExampleAccess;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;

import com.google.common.collect.ImmutableList;

@Test(groups = { "Live" })
public class MySqlLiveGceTest extends AbstractGoogleComputeLiveTest {

    @Override
    protected void doTest(Location loc) throws Exception {
        MySqlNode mysql = app.createAndManageChild(EntitySpec.create(MySqlNode.class)
                .configure(DatastoreCommon.CREATION_SCRIPT_CONTENTS, MySqlIntegrationTest.CREATION_SCRIPT));

        app.start(ImmutableList.of(loc));

        new VogellaExampleAccess("com.mysql.jdbc.Driver", mysql.getAttribute(DatastoreCommon.DATASTORE_URL)).readModifyAndRevertDataBase();
    }

    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods  

}


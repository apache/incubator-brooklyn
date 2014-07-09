package brooklyn.entity.database.mariadb;

import org.testng.annotations.Test;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.database.DatastoreMixins.DatastoreCommon;
import brooklyn.entity.database.VogellaExampleAccess;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;

import com.google.common.collect.ImmutableList;

@Test(groups = { "Live" })
public class MariaDbLiveEc2Test extends AbstractEc2LiveTest {

    @Override
    protected void doTest(Location loc) throws Exception {

        MariaDbNode mariadb = app.createAndManageChild(EntitySpec.create(MariaDbNode.class)
                .configure(DatastoreCommon.CREATION_SCRIPT_CONTENTS, MariaDbIntegrationTest.CREATION_SCRIPT));

        app.start(ImmutableList.of(loc));

        new VogellaExampleAccess("com.mysql.jdbc.Driver", mariadb.getAttribute(DatastoreCommon.DATASTORE_URL)).readModifyAndRevertDataBase();
    }

    @Override
    @Test(enabled=false, groups = "Live")
    public void test_Debian_7_2() throws Exception { } // Disabled because MariaDB not available

    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods  

}


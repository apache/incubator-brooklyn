package brooklyn.entity.database.postgresql;

import brooklyn.entity.database.DatastoreMixins.DatastoreCommon;
import brooklyn.entity.database.VogellaExampleAccess;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.software.AbstractDockerLiveTest;
import brooklyn.location.Location;
import com.google.common.collect.ImmutableList;
import org.testng.annotations.Test;

public class PostgreSqDockerLiveTest extends AbstractDockerLiveTest {

    @Override
    protected void doTest(Location loc) throws Exception {
        PostgreSqlNode psql = app.createAndManageChild(EntitySpec.create(PostgreSqlNode.class)
                .configure(DatastoreCommon.CREATION_SCRIPT_CONTENTS, PostgreSqlIntegrationTest.CREATION_SCRIPT));

        app.start(ImmutableList.of(loc));

        new VogellaExampleAccess("org.postgresql.Driver", psql.getAttribute(DatastoreCommon.DATASTORE_URL)).readModifyAndRevertDataBase();
    }

    @Test(enabled=false)
    public void testDummy() { } // Convince testng IDE integration that this really does have test methods
}


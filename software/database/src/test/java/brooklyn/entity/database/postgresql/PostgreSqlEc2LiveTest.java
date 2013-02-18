package brooklyn.entity.database.postgresql;

import org.testng.annotations.Test;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.database.VogellaExampleAccess;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.location.Location;

import com.google.common.collect.ImmutableList;

public class PostgreSqlEc2LiveTest extends AbstractEc2LiveTest {

    @Override
    protected void doTest(Location loc) throws Exception {
        PostgreSqlNode psql = app.createAndManageChild(BasicEntitySpec.newInstance(PostgreSqlNode.class)
                .configure("creationScriptContents", PostgreSqlIntegrationTest.CREATION_SCRIPT));

        app.start(ImmutableList.of(loc));

        String host = psql.getAttribute(PostgreSqlNode.HOSTNAME);
        int port = psql.getAttribute(PostgreSqlNode.POSTGRESQL_PORT);
        new VogellaExampleAccess("org.postgresql.Driver", "postgresql", host, port).readModifyAndRevertDataBase();
    }
    
    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods  
}


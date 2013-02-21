package brooklyn.entity.database.mysql;

import org.testng.annotations.Test;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.database.VogellaExampleAccess;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.location.Location;

import com.google.common.collect.ImmutableList;

public class MySqlLiveEc2Test extends AbstractEc2LiveTest {

    @Override
    protected void doTest(Location loc) throws Exception {
        MySqlNode mysql = app.createAndManageChild(BasicEntitySpec.newInstance(MySqlNode.class)
                .configure("creationScriptContents", MySqlIntegrationTest.CREATION_SCRIPT));

        app.start(ImmutableList.of(loc));

        new VogellaExampleAccess("com.mysql.jdbc.Driver", mysql.getAttribute(MySqlNode.DB_URL)).readModifyAndRevertDataBase();
    }
    
    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods  
}


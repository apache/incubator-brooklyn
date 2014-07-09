package brooklyn.entity.chef.mysql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.chef.ChefLiveTestSupport;
import brooklyn.entity.chef.ChefServerTasksIntegrationTest;
import brooklyn.location.NoMachinesAvailableException;

/** Expects knife on the path, but will use Brooklyn registered account,
 * and that account has the mysql recipe installed.
 * <p>
 * See {@link ChefServerTasksIntegrationTest} for more info. */
public class DynamicChefServerToyMySqlEntityLiveTest extends AbstractChefToyMySqlEntityLiveTest {

    private static final Logger log = LoggerFactory.getLogger(DynamicChefServerToyMySqlEntityLiveTest.class);
    
    // test here just so Eclipse IDE picks it up
    @Override @Test(groups="Live")
    public void testMySqlOnProvisioningLocation() throws NoMachinesAvailableException {
        super.testMySqlOnProvisioningLocation();
    }
    
    @Override
    protected Entity createMysql() {
        ChefLiveTestSupport.installBrooklynChefHostedConfig(app);
        Entity mysql = app.createAndManageChild(DynamicToyMySqlEntityChef.specKnife());
        log.debug("created "+mysql);
        return mysql;
    }
    
}

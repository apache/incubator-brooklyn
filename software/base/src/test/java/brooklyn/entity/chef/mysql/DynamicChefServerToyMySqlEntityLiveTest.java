package brooklyn.entity.chef.mysql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.chef.ChefConfig;
import brooklyn.entity.chef.ChefServerTasksIntegrationTest;
import brooklyn.location.NoMachinesAvailableException;

/** Expects knife on the path, but will use Brooklyn registered account,
 * and that account has the mysql recipe installed.
 * <p>
 * See {@link ChefServerTasksIntegrationTest} for more info. */
public class DynamicChefServerToyMySqlEntityLiveTest extends AbstractChefToyMySqlEntityLiveTest {

    private static final Logger log = LoggerFactory.getLogger(DynamicChefServerToyMySqlEntityLiveTest.class);
    
    @BeforeMethod(alwaysRun=true)
    public void installKnifeConfig() {
        ChefServerTasksIntegrationTest.installBrooklynChefHostedConfig();
    }
    
    // test here just so Eclipse IDE picks it up
    @Override @Test(groups="Live")
    public void testMySqlOnProvisioningLocation() throws NoMachinesAvailableException {
        super.testMySqlOnProvisioningLocation();
    }
    
    @Override
    protected Entity createMysql() {
        Entity mysql = app.createAndManageChild(DynamicToyMySqlEntityChef.specKnife());
        app.setConfig(ChefConfig.KNIFE_CONFIG_FILE, ChefServerTasksIntegrationTest.installBrooklynChefHostedConfig());
        log.debug("created "+mysql);
        return mysql;
    }
    
}

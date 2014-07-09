package brooklyn.entity.chef.mysql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.location.NoMachinesAvailableException;

public class DynamicChefAutodetectToyMySqlEntityLiveTest extends AbstractChefToyMySqlEntityLiveTest {

    private static final Logger log = LoggerFactory.getLogger(DynamicChefAutodetectToyMySqlEntityLiveTest.class);
    
    // test here just so Eclipse IDE picks it up
    @Override @Test(groups="Live")
    public void testMySqlOnProvisioningLocation() throws NoMachinesAvailableException {
        super.testMySqlOnProvisioningLocation();
    }
    
    @Override
    protected Entity createMysql() {
        Entity mysql = app.createAndManageChild(DynamicToyMySqlEntityChef.spec());
        log.debug("created "+mysql);
        return mysql;
    }
    
}

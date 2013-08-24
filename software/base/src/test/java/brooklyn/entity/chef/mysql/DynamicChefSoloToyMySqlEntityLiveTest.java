package brooklyn.entity.chef.mysql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.location.NoMachinesAvailableException;

public class DynamicChefSoloToyMySqlEntityLiveTest extends AbstractChefToyMySqlEntityLiveTest {

    private static final Logger log = LoggerFactory.getLogger(DynamicChefSoloToyMySqlEntityLiveTest.class);
    
    // test here just so Eclipse IDE picks it up
    @Override @Test(groups="Live")
    public void testMySqlOnProvisioningLocation() throws NoMachinesAvailableException {
        super.testMySqlOnProvisioningLocation();
    }
    
    @Override
    protected Entity createMysql() {
        Entity mysql = app.createAndManageChild(DynamicToyMySqlEntityChefBuilder.spec());
        DynamicToyMySqlEntityChefBuilder.makeMySql((EntityInternal) mysql);
        log.debug("created "+mysql);
        return mysql;
    }
    
}

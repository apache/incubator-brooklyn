package brooklyn.entity.software.mysql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.location.NoMachinesAvailableException;


public class DynamicToyMySqlEntityTest extends AbstractToyMySqlEntityTest {

    private static final Logger log = LoggerFactory.getLogger(DynamicToyMySqlEntityTest.class);
    
    protected Entity createMysql() {
        Entity mysql = app.createAndManageChild(DynamicToyMySqlEntityBuilder.spec());
        DynamicToyMySqlEntityBuilder.makeMySql((EntityInternal) mysql);
        log.debug("created "+mysql);
        return mysql;
    }

    // test here just so Eclipse IDE picks it up
    @Test(groups="Integration")
    public void testMySqlOnMachineLocation() throws NoMachinesAvailableException {
        super.testMySqlOnMachineLocation();
    }

}

package brooklyn.entity.chef.mysql;

import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.util.task.system.ProcessTaskWrapper;

public class ChefSoloDriverMySqlEntityLiveTest extends AbstractChefToyMySqlEntityLiveTest {

    // test here just so Eclipse IDE picks it up
    @Override @Test(groups="Live")
    public void testMySqlOnProvisioningLocation() throws NoMachinesAvailableException {
        super.testMySqlOnProvisioningLocation();
    }

    @Override
    protected Integer getPid(Entity mysql) {
        ProcessTaskWrapper<Integer> t = Entities.submit(mysql, SshEffectorTasks.ssh("sudo cat "+ChefDriverToyMySqlEntity.PID_FILE));
        return Integer.parseInt(t.block().getStdout().trim());
    }
    
    @Override
    protected Entity createMysql() {
        return app.createAndManageChild(EntitySpec.create(Entity.class, ChefDriverToyMySqlEntity.class).
                additionalInterfaces(SoftwareProcess.class));
    }
    
}

package brooklyn.event.adapter

import static org.testng.Assert.assertTrue

import org.testng.annotations.Test

import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.basic.lifecycle.ScriptRunner
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.location.basic.LocalhostMachineProvisioningLocation.LocalhostMachine
import brooklyn.test.entity.TestEntity

public class SshShellSensorAdapterTest extends ShellSensorAdapterTest {
    
    @Test
    @Override
    public void testDiskFree() {
        def driver = new ScriptRunner() {
            public int execute(Map flags=[:], List<String> script, String summaryForLogging) {
                def machine = new LocalhostMachine()
                return machine.run(flags, script);
            }
        }
        registerAdapter(new SshShellSensorAdapter(driver, "df -b"))
            .then(this.&parseDf)
            .with {
                poll(TestEntity.SEQUENCE) {
                    log.debug("disk stats: "+it)
                    it[0].totalBytes 
                }
            }
        
        adapter.poller.executePoll();
        //fails if disk is totally empty as well as any error...
        assertTrue(entity.getAttribute(TestEntity.SEQUENCE) > 0);
    }
}

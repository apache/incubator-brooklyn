package brooklyn.event.adapter

import static org.testng.Assert.assertTrue

import java.util.List;
import java.util.Map;

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.Test

import com.google.common.collect.Iterables

import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.basic.lifecycle.ScriptRunner
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestEntity

public class SshShellSensorAdapterTest extends ShellSensorAdapterTest {
    public static final Logger log = LoggerFactory.getLogger(SshShellSensorAdapterTest.class)
    
    @Test(groups = "WIP")   // is quite sensitive, and parent fails in build server, so disabling;
    // also probably better to only do ssh localhost for integration tests (in case people don't have loopback ssh allowed)?
    @Override
    public void testDiskFree() {
        def driver = new ScriptRunner() {
            LocalhostMachineProvisioningLocation location = [ count:1 ]
            public int execute(Map flags=[:], List<String> script, String summaryForLogging) {
                return location.obtain().run(flags, script);
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

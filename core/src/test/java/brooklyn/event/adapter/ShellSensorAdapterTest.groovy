package brooklyn.event.adapter;

import static org.testng.Assert.assertTrue

import org.testng.annotations.Test

import brooklyn.entity.basic.EntityLocal
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.test.entity.TestEntity

public class ShellSensorAdapterTest {

    final EntityLocal entity = new TestEntity();
    final SensorRegistry entityRegistry = new SensorRegistry(entity);

    ShellSensorAdapter adapter;
    public ShellSensorAdapter registerAdapter(ShellSensorAdapter adapter=null) {
        if (adapter!=null) this.adapter = adapter;
        else adapter = this.adapter;
        adapter.pollPeriod = null;
        entityRegistry.register(adapter);
        entityRegistry.activateAdapters();
        adapter;
    }
    
    // fails on build server, so disabling for now
    @Test(groups = "WIP")
    public void testDiskFree() {
        registerAdapter(new ShellSensorAdapter("df -b")).
            then(this.&parseDf).with {
                poll(TestEntity.SEQUENCE, {
                    log.debug("disk stats: "+it)
                    it[0].totalBytes 
                })
            }
        
        adapter.poller.executePoll();
        //fails if disk is totally empty as well as any error...
        assertTrue(entity.getAttribute(TestEntity.SEQUENCE) > 0);
    }
    
    public static List parseDf(String[] lines) {
//            Filesystem    512-blocks      Used Available Capacity  Mounted on
        List result = [];
//            /dev/disk0s2   624470624 585074728  38883896    94%    /
//            devfs                213       213         0   100%    /dev
        //ignore first line
        for (int i=1; i<lines.length; i++) {
            //split on whitespace
            String[] fields = lines[i].split("\\s+");  
            result << [usageBytes:fields[2], totalBytes:fields[1], name:fields[0]];
        }
        result
    }
}

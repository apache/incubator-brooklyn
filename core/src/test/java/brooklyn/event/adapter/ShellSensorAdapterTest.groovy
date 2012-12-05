package brooklyn.event.adapter;

import static org.testng.Assert.assertTrue

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.Entities
import brooklyn.entity.basic.EntityLocal
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity

public class ShellSensorAdapterTest {
    static final Logger log = LoggerFactory.getLogger(ShellSensorAdapterTest)

    Application app;
    EntityLocal entity;
    SensorRegistry entityRegistry;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = new TestApplication();
        entity = new TestEntity(app);
        Entities.startManagement(app);
        entityRegistry = new SensorRegistry(entity);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroy(app);
    }
    
    ShellSensorAdapter adapter;
    public ShellSensorAdapter registerAdapter(ShellSensorAdapter adapter=null) {
        if (adapter!=null) this.adapter = adapter;
        else adapter = this.adapter;
        adapter.pollPeriod = null;
        entityRegistry.register(adapter);
        adapter;
    }
    
    // fails on build server, so disabling for now
    @Test(groups = "WIP")
    public void testDiskFree() {
        registerAdapter(new ShellSensorAdapter("df -b")).
            then(this.&parseDf).with {
                poll(TestEntity.SEQUENCE) {
                    log.debug("disk stats: "+it)
                    it[0].totalBytes 
                }
            }
        
        adapter.poller.executePoll();
        //fails if disk is totally empty as well as any error...
        assertTrue(entity.getAttribute(TestEntity.SEQUENCE) > 0);
    }
    
    public static List parseDf(String[] lines) {
        List result = [];
		// Filesystem    512-blocks      Used Available Capacity  Mounted on
		// /dev/disk0s2   624470624 585074728  38883896    94%    /
		// devfs                213       213         0   100%    /dev
        int i = 0
        // Ignore lines until Filesystem line
        for (; i < lines.length; i++) {
            if (lines[i] =~ /Filesystem/) break
        }
        // Parse data by splitting on whitespace
        for (++i; i < lines.length; i++) {
            if (lines[i] =~ /exit/) break
            String[] fields = lines[i].split("\\s+");  
            result << [usageBytes:fields[2], totalBytes:fields[1], name:fields[0]];
        }
        result
    }
}

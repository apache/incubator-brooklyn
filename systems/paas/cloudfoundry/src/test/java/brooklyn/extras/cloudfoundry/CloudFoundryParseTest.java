package brooklyn.extras.cloudfoundry;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.extras.cloudfoundry.CloudFoundryVmcCliAccess.CloudFoundryAppStatLine;
import brooklyn.extras.cloudfoundry.CloudFoundryVmcCliAccess.CloudFoundryAppStats;

public class CloudFoundryParseTest {

    public static final Logger log = LoggerFactory.getLogger(CloudFoundryParseTest.class);
    
    @Test
    public void testParseStatsLine() {
        CloudFoundryAppStatLine stats = CloudFoundryAppStatLine.parse(
                "| 0        | 0.0% (4)    | 116.6M (512M)  | 9.5M (2G)    | 0d:15h:41m:2s |");
        log.info("stats: "+stats);
        Assert.assertEquals(stats.getCpuUsage(), 0d);
        Assert.assertEquals(stats.getNumCores(), 4);
        Assert.assertEquals(stats.getMemUsedMB(), 116.6d, 0.0000001);
        Assert.assertEquals(stats.getMemLimitMB(), 512d, 0.0000001);
        Assert.assertEquals(stats.getMemUsedFraction(), 116.6d/512d, 0.0000001);
        Assert.assertEquals(stats.getDiskUsedMB(), 9.5d, 0.0000001);
        Assert.assertEquals(stats.getDiskLimitMB(), 2048d, 0.0000001);
        Assert.assertEquals(stats.getDiskUsedFraction(), 9.5d/2048d, 0.0000001);
        Assert.assertEquals(stats.getUptimeSeconds(), 60*(60*15+41)+2);
    }

    @Test
    public void testParseStatsAverage() {
        CloudFoundryAppStatLine stats1 = CloudFoundryAppStatLine.parse(
                "| 0        | 0.0% (4)    | 116.6M (512M)  | 9.5M (2G)    | 0d:15h:41m:2s |");
        CloudFoundryAppStatLine stats2 = CloudFoundryAppStatLine.parse(
                "| 0        | 10.0% (4)    | 316.6M (512M)  | 29.5M (2G)    | 0d:13h:41m:2s |");
        
        CloudFoundryAppStats stats = new CloudFoundryAppStats();
        stats.setInstances( Arrays.asList(stats1, stats2));
        stats.setAverage(CloudFoundryAppStatLine.average(stats.getInstances()));
        
        CloudFoundryAppStatLine avg = stats.getAverage();
        
        Assert.assertEquals(avg.getCpuUsage(), 0.05d);
        Assert.assertEquals(avg.getNumCores(), 4);
        Assert.assertEquals(avg.getMemUsedMB(), 216.6d, 0.0000001);
        Assert.assertEquals(avg.getMemLimitMB(), 512d, 0.0000001);
        Assert.assertEquals(avg.getMemUsedFraction(), 216.6d/512d, 0.0000001);
        Assert.assertEquals(avg.getDiskUsedMB(), 19.5d, 0.0000001);
        Assert.assertEquals(avg.getDiskLimitMB(), 2048d, 0.0000001);
        Assert.assertEquals(avg.getDiskUsedFraction(), 19.5d/2048d, 0.0000001);
        Assert.assertEquals(avg.getUptimeSeconds(), 60*(60*14+41)+2);
    }

}

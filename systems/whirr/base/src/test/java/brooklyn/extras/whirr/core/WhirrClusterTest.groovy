package brooklyn.extras.whirr.core

import org.testng.Assert;
import org.testng.annotations.Test;

class WhirrClusterTest {

    @Test
    public void testControllerInitialized() {
        WhirrCluster wc = new WhirrCluster([:], null);
        Assert.assertNotNull(wc.controller);
    }
    
}

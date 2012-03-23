package brooklyn.extras.whirr.hadoop

import org.testng.annotations.Test;

class WhirrHadoopClusterTest {

    @Test(expectedExceptions = [ IllegalArgumentException ])
    public void testSizeTooSmall() {
        WhirrHadoopCluster whc = new WhirrHadoopCluster(size: 1, null);
        whc.generateWhirrClusterRecipe();
    }

    @Test
    public void testDefaultsDontFailInRecipeGeneration() {
        WhirrHadoopCluster whc = new WhirrHadoopCluster([:], null);
        whc.generateWhirrClusterRecipe();
    }

}

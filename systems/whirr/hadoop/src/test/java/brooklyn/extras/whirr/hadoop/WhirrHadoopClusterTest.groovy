package brooklyn.extras.whirr.hadoop

import org.testng.annotations.Test

class WhirrHadoopClusterTest {

    @Test(expectedExceptions = [ IllegalArgumentException ])
    public void testSizeTooSmall() {
        WhirrHadoopCluster whc = new WhirrHadoopClusterImpl(size: 1, null);
        whc.generateWhirrClusterRecipe();
    }

    @Test
    public void testDefaultsDontFailInRecipeGeneration() {
        WhirrHadoopCluster whc = new WhirrHadoopClusterImpl([:], null);
        whc.generateWhirrClusterRecipe();
    }

}

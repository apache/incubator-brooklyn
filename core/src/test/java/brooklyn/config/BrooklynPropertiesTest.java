package brooklyn.config;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class BrooklynPropertiesTest {

    private void assertForSample(BrooklynProperties p) {
        Assert.assertEquals(p.getFirst("P1"), "Property 1");
        Assert.assertEquals(p.getFirst("P2"), "Property 2");
        Assert.assertEquals(p.getFirst("P0", "P3"), null);
    }
    
    private void assertForMoreSample(BrooklynProperties p) {
        Assert.assertEquals(p.getFirst("P1"), "Property 1 v2");
        Assert.assertEquals(p.getFirst("P2"), "Property 2");
        Assert.assertEquals(p.getFirst("P0", "P3"), "Property 3");
    }
    
    private void assertForSampleAndTricky(BrooklynProperties p) {
        Assert.assertEquals(p.getFirst("P1"), "Property 1 v4");
        Assert.assertEquals(p.getFirst("P2"), "Property 2");
        Assert.assertEquals(p.getFirst("P0", "P3"), null);
        Assert.assertEquals(p.getFirst("a.b.c"), "d.e.f");
        Assert.assertEquals(p.getFirst("a"), "b=c");
        Assert.assertEquals(p.getFirst("aa"), "$a");
    }
    
    public void testSample() {
        BrooklynProperties p1 = BrooklynProperties.Factory.newEmpty().addFromUrl("brooklyn/config/sample.properties");
        assertForSample(p1);
    }
    
    public void testSampleFromClasspathUrl() {
        BrooklynProperties p1 = BrooklynProperties.Factory.newEmpty().addFromUrl("classpath://brooklyn/config/sample.properties");
        assertForSample(p1);
    }

    public void testMoreSample() {
        BrooklynProperties p1 = BrooklynProperties.Factory.newEmpty().
                addFromUrl("brooklyn/config/sample.properties").
                addFromUrl("brooklyn/config/more-sample.properties");
        assertForMoreSample(p1);
    }

//            P1=Property 1 v3
//            P1=Property 1 v4
//            a.b.c=d.e.f
//            a=b=c
//            aa=$a

    public void testTricky() {
        BrooklynProperties p1 = BrooklynProperties.Factory.newEmpty().
                addFromUrl("brooklyn/config/sample.properties").
                addFromUrl("brooklyn/config/tricky.properties");
        assertForSampleAndTricky(p1);
    }

}

package brooklyn.config;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.NoSuchElementException;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.MutableMap;

import com.google.common.collect.ImmutableMap;

@Test
public class BrooklynPropertiesTest {

    @Test
    public void testGetFirst() {
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty().addFromMap(ImmutableMap.of("akey", "aval", "bkey", "bval"));
        
        assertEquals(props.getFirst("akey"), "aval");
        assertEquals(props.getFirst("akey", "bkey"), "aval");
        assertEquals(props.getFirst("akey", "notThere"), "aval");
               
        assertEquals(props.getFirst("notThere"), null);
        assertEquals(props.getFirst("notThere", "notThere2"), null);
    }

    @Test
    public void testGetFirstUsingFailIfNone() {
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty().addFromMap(ImmutableMap.of("akey", "aval", "bkey", "bval"));
        assertEquals(props.getFirst(MutableMap.of("failIfNone", true), "akey"), "aval");

        try {
            props.getFirst(MutableMap.of("failIfNone", true), "notThrere");
            fail();
        } catch (NoSuchElementException e) {
            // success
        }
    }

    @Test
    public void testGetFirstUsingFailIfNoneFalse() {
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty().addFromMap(ImmutableMap.of("akey", "aval", "bkey", "bval"));
        assertEquals(props.getFirst(MutableMap.of("failIfNone", false), "notThrere"), null);
    }

    @Test
    public void testGetFirstUsingDefaultIfNone() {
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty().addFromMap(ImmutableMap.of("akey", "aval", "bkey", "bval"));
        assertEquals(props.getFirst(MutableMap.of("defaultIfNone", "mydef"), "akey"), "aval");
        assertEquals(props.getFirst(MutableMap.of("defaultIfNone", "mydef"), "notThere"), "mydef");
    }
    
    /*
     * sample.properties:
     *   P1=Property 1
     *   P2=Property 2
     * 
     * more-sample.properties:
     *   P3=Property 3
     *   P1=Property 1 v2
     * 
     * tricky.properties:
     *   P1=Property 1 v3
     *   P1=Property 1 v4
     *   a.b.c=d.e.f
     *   a=b=c
     *   aa=$a
     */
    
    @Test
    public void testAddFromUrlSimple() {
        BrooklynProperties p1 = BrooklynProperties.Factory.newEmpty().addFromUrl("brooklyn/config/sample.properties");
        assertForSample(p1);
    }
    
    @Test
    public void testAddFromUrlClasspath() {
        BrooklynProperties p1 = BrooklynProperties.Factory.newEmpty().addFromUrl("classpath://brooklyn/config/sample.properties");
        assertForSample(p1);
    }

    @Test
    public void testAddMultipleFromUrl() {
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

    @Test
    public void testTrickyAddMultipleFromUrl() {
        BrooklynProperties p1 = BrooklynProperties.Factory.newEmpty().
                addFromUrl("brooklyn/config/sample.properties").
                addFromUrl("brooklyn/config/tricky.properties");
        assertForSampleAndTricky(p1);
    }

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
}

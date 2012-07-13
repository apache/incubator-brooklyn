package brooklyn.config;

import static org.testng.Assert.assertEquals
import static org.testng.Assert.fail

import org.testng.annotations.Test

import brooklyn.util.MutableMap

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap

public class BrooklynPropertiesFromGroovyTest {

    @Test
    public void testGetFirstUsingFailIfNoneWithClosure() {
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty().addFromMap(ImmutableMap.of("akey", "aval", "bkey", "bval"));
        Object keys;
        try {
            props.getFirst(MutableMap.of("failIfNone", { keys = it }), "notThere");
        } catch (NoSuchElementException e) {
            // expected
        }
        assertEquals(keys, "notThere");
    }
    
    @Test
    public void testGetFirstMultiArgUsingFailIfNoneWithClosure() {
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty().addFromMap(ImmutableMap.of("akey", "aval", "bkey", "bval"));
        Object keys;
        try {
            props.getFirst(MutableMap.of("failIfNone", { it1, it2 -> keys = [it1, it2] }), "notThere", "notThere2");
        } catch (NoSuchElementException e) {
            // expected
        }
        assertEquals(keys, ImmutableList.of("notThere", "notThere2"));
    }
}

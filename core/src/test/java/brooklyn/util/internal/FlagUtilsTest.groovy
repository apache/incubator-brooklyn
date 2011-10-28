package brooklyn.util.internal;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.SetFromFlag;

public class FlagUtilsTest {

    static class Foo {
        @SetFromFlag
        int x;
        
        @SetFromFlag("y")
        int yNotY;
    }
    
    @Test
    public void testSetFieldsFromFlags() {
        Foo f = []
        def unused = FlagUtils.setFieldsFromFlags(f, x:1, y:7, z:9);
        assertEquals(f.x, 1)
        assertEquals(f.yNotY, 7)
        assertEquals(unused, [z:9])
    }

}

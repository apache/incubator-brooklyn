package brooklyn.util.internal;

import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.Test

import brooklyn.config.ConfigKey
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.trait.Configurable
import brooklyn.event.basic.BasicConfigKey
import brooklyn.util.config.ConfigBag
import brooklyn.util.flags.FlagUtils
import brooklyn.util.flags.SetFromFlag

public class FlagUtilsTest {

	public static final Logger log = LoggerFactory.getLogger(FlagUtilsTest.class);
	
	@Test
	public void testGetAllFields() {
		log.info "types {}", FlagUtils.getAllAssignableTypes(Baz)
		assertEquals FlagUtils.getAllAssignableTypes(Baz), [ Baz, Foo, Bar ];
		def fs = FlagUtils.getAllFields(Baz);
		fs.each { log.info "field {}    {}", it.name, it }
		List fsn = fs.collect { it.name }
		assertTrue fsn.indexOf("A") >= 0
		assertTrue fsn.indexOf("w") > fsn.indexOf("A") 
		assertTrue fsn.indexOf("x") > fsn.indexOf("A") 
		assertTrue fsn.indexOf("yNotY") > fsn.indexOf("A") 
		assertTrue fsn.indexOf("Z") > fsn.indexOf("yNotY") 
	}	
	
    @Test
    public void testSetFieldsFromFlags() {
        Foo f = []
		Map m = [w:3, x:1, y:7, z:9]
        def unused = FlagUtils.setFieldsFromFlags(m, f);
		assertEquals(f.w, 3)
        assertEquals(f.x, 1)
        assertEquals(f.yNotY, 7)
        assertEquals(unused, [z:9])
		Map m2 = FlagUtils.getFieldsWithValues(f)
		m -= unused
		assertEquals m2, m
    }
    
    @Test
    public void testCollectionCoercionOnSetFromFlags() {
        WithSpecialFieldTypes s = []
        Map m = [set:[1]]
        def unused = FlagUtils.setFieldsFromFlags(m, s);
        assertEquals(s.set, [1])
    }

    @Test
    public void testInetAddressCoercionOnSetFromFlags() {
        WithSpecialFieldTypes s = []
        Map m = [inet:"127.0.0.1"]
        def unused = FlagUtils.setFieldsFromFlags(m, s);
        assertEquals(s.inet?.address, [127, 0, 0, 1] as byte[])
    }

    @Test
    public void testNonImmutableField() {
        Foo f = []
        FlagUtils.setFieldsFromFlags(f, w:8);
        assertEquals f.w, 8
        FlagUtils.setFieldsFromFlags(f, w:9);
        assertEquals f.w, 9
    }

    @Test
    public void testImmutableIntField() {
        Foo f = []
        FlagUtils.setFieldsFromFlags(f, x:8);
        assertEquals f.x, 8
        boolean succeededWhenShouldntHave = false 
        try {
            FlagUtils.setFieldsFromFlags(f, x:9);
            succeededWhenShouldntHave = true
        } catch (IllegalStateException e) {
            //expected
        }
        assertFalse succeededWhenShouldntHave
        assertEquals f.x, 8
    }

    @Test
    public void testImmutableObjectField() {
        WithImmutableNonNullableObject o = []
        FlagUtils.setFieldsFromFlags(o, a:"a", b:"b");
        assertEquals o.a, "a"
        assertEquals o.b, "b"
        
        FlagUtils.setFieldsFromFlags(o, a:"a2");
        assertEquals o.a, "a2"
        
        boolean succeededWhenShouldntHave = false
        try {
            FlagUtils.setFieldsFromFlags(o, b:"b2");
            succeededWhenShouldntHave = true
        } catch (IllegalStateException e) {
            //expected
        }
        assertFalse succeededWhenShouldntHave
        assertEquals o.b, "b"
    }

    @Test
    public void testNonNullable() {
        WithImmutableNonNullableObject o = []
        //allowed
        FlagUtils.setFieldsFromFlags(o, a:null);
        assertEquals o.a, null
        assertEquals o.b, null
        //not allowed
        boolean succeededWhenShouldntHave = false
        try {
            FlagUtils.setFieldsFromFlags(o, b:null);
            succeededWhenShouldntHave = true
        } catch (IllegalArgumentException e) {
            //expected
        }
        assertFalse succeededWhenShouldntHave
        assertEquals o.b, null
    }
    
    @Test
    public void testGetAnnotatedFields() {
        def fm = FlagUtils.getAnnotatedFields(WithImmutableNonNullableObject)
        assertEquals fm.keySet().size(), 2
        assertTrue fm.get(WithImmutableNonNullableObject.class.getDeclaredField("b")).immutable()
    }

    @Test
    public void testCheckRequired() {
        WithImmutableNonNullableObject f = []
        def unused = FlagUtils.setFieldsFromFlags(f, a:"a is a");
        assertEquals(f.a, "a is a")
        assertEquals(f.b, null)
        int exceptions = 0;
        try {
            FlagUtils.checkRequiredFields(f)
        } catch (IllegalStateException e) {
            exceptions++;
        }
        assertEquals exceptions, 1
    }

    @Test
    public void testSetConfigKeys() {
        FooCK f = []
        def unused = FlagUtils.setFieldsFromFlags(f, f1: 9, ck1:"do-set", ck2:"dont-set");
        assertEquals(f.bag.get(FooCK.CK1), "do-set")
        assertEquals(f.f1, 9)
        assertEquals(f.bag.containsKey(FooCK.CK2), false)
        if (unused.size()!=1 || !unused.ck2) fail("Wrong unused contents: "+unused);
    }
    
    @Test
    public void testSetAllConfigKeys() {
        FooCK f = []
        def unused = FlagUtils.setAllConfigKeys(f1: 9, ck1:"do-set", ck2:"do-set-2", f);
        assertEquals(f.bag.get(FooCK.CK1), "do-set")
        assertEquals(f.bag.containsKey(FooCK.CK2), true)
        assertEquals(f.bag.get(FooCK.CK2), "do-set-2")
        if (unused.size()!=1 || !unused.f1) fail("Wrong unused contents: "+unused);
    }

    @Test
    public void testSetFromConfigKeys() {
        FooCK f = []
        def unused = FlagUtils.setFieldsFromFlags(f, (new BasicConfigKey<Integer>(Integer.class, "f1")): 9, ck1:"do-set", ck2:"dont-set");
        assertEquals(f.bag.get(FooCK.CK1), "do-set")
        assertEquals(f.f1, 9)
        assertEquals(f.bag.containsKey(FooCK.CK2), false)
        if (unused.size()!=1 || !unused.ck2) fail("Wrong unused contents: "+unused);
    }

}

class Foo {
	@SetFromFlag
	int w;
	
	@SetFromFlag(immutable=true)
	private int x;
	
	@SetFromFlag("y")
	public int yNotY;
}

interface Bar {
	static String Z;
}

class Baz extends Foo implements Bar {
	private static int A;
}

class WithImmutableNonNullableObject {
    @SetFromFlag
    Object a;
    @SetFromFlag(immutable=true, nullable=false)
    public Object b;
}

class WithSpecialFieldTypes {
    @SetFromFlag Set set;
    @SetFromFlag InetAddress inet;
}

class FooCK implements Configurable {
    @SetFromFlag
    public static ConfigKey<String> CK1 = ConfigKeys.newStringConfigKey("ck1");
    
    public static ConfigKey<String> CK2 = ConfigKeys.newStringConfigKey("ck2");

    @SetFromFlag
    int f1;
    
    ConfigBag bag = [];
    public <T> T setConfig(ConfigKey<T> key, T val) {
        bag.put(key, val);
    }
}

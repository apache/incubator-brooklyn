package brooklyn.util.internal;

import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.Test

import brooklyn.util.internal.LanguageUtils.FieldVisitor


/**
 * Test the operation of the {@link LanguageUtils} utilities.
 */
public class LanguageUtilsTest {
    private static final Logger log = LoggerFactory.getLogger(LanguageUtilsTest.class)
 
    @Test
    public void testSetFieldsFromMap() {
        A a = []
        Map unused = LanguageUtils.setFieldsFromMap(a, [num:1,mun:2])
        assertEquals(1, a.num);
        assertEquals([mun:2], unused)
    }
    
    @Test
    public void testVisitingFieldsDeepNonLooping() {
        BigUn b2 = new BigUn(name:"L'il Guy", num:10, dates:[ new Date() ])
//        b2.dates = [ new Date() ] as Date[]
        BigUn b1 = new BigUn(name:"Big Guy", num:40)
        b1.child = b2;
        b1.children += b2
        b2.child = b1
        
        int sum = 0;
        FieldVisitor numSummer = { parent, name, value -> if ("num"==name) sum+=value } as FieldVisitor
        LanguageUtils.visitFields(b1, numSummer)
        
        assertEquals(50, sum) 
    }
    
    private static class A {
        int num;
    }
    
    private static class BigUn {
        String name;
        int num;
        BigUn child;
        Set children = []
        Date[] dates;
    }

    //test the default getter, and equals
    static class TestingFieldA {
        public int a = 6;
        int getAt(A aa) { return aa.num * a; }
        static A aa = [num:10];
        int x = -1;
    }
    static class TestingFields extends TestingFieldA {
        int b = 7;
        int getB() { -7 }
        public int c = 8;
        int getD() { 9 }
    }
    @Test
    public void testSomeGet() {
        TestingFields tf = []
        assertEquals( [6, -7, 7, 8, 9, 60],
            ["a", "b", "@b", "c", "d", TestingFields.aa].collect {
                LanguageUtils.DEFAULT_FIELD_GETTER.call(tf, it)
            })
    }
    
    @Test
    public void testEquals() {
        //basic
        TestingFields t1 = [], t2 = []
        assertTrue LanguageUtils.equals(t1, t2, null, ["a", "b"])
        assertTrue LanguageUtils.equals(t1, t2, TestingFields, ["a", "b"])
        assertFalse LanguageUtils.equals(t1, t2, String, ["a", "b"])
        assertFalse LanguageUtils.equals(t1, t2, null, ["z"])
        assertTrue LanguageUtils.equals(t1, t2, null, (["a", "b"] as String[]))
        
        //type hierarchy
        TestingFieldA t1a = []
        assertTrue LanguageUtils.equals(t1, t1a, null, "a")
        assertTrue LanguageUtils.equals(t1, t1a, TestingFieldA, "a")
        assertFalse LanguageUtils.equals(t1, t1a, TestingFields, "a")
        assertFalse LanguageUtils.equals(t1, t1a, null, "a", "b")
        t1.b = 0
        assertTrue LanguageUtils.equals(t1, t1a, null, "a")
        t1a.a = -6
        assertFalse LanguageUtils.equals(t1, t1a, null, "a")
        
        //direct access to field
        assertTrue LanguageUtils.equals(t1, t2, null, "b")
        assertFalse LanguageUtils.equals(t1, t2, null, "@b")
        assertTrue LanguageUtils.equals(t1, t2, null, "@a")
        
        //and complex field
        assertTrue LanguageUtils.equals(t1, t2, null, TestingFields.aa)
        //because we changed t1a.a, and getAt(A) refers to int a
        assertFalse LanguageUtils.equals(t1, t1a, null, TestingFields.aa)
        
        //test it works with POJO objects (non-groovy)
        assertTrue LanguageUtils.equals(new PojoTestingFields(1), new PojoTestingFields(1), null, "privateInt")
        assertFalse LanguageUtils.equals(new PojoTestingFields(1), new PojoTestingFields(2), null, "privateInt")
        
        //and a tricky one, because x is a groovy property, it is _private_ so we cannot see it as a field wrt t1
        assertFalse LanguageUtils.equals(t1, t1a, null, "@x")
        //but in the context of t1a we can.. in short, be careful with fields
        assertTrue LanguageUtils.equals(t1a, t1a, null, "@x")
    }

    @Test
    public void testHashCode() {
        //basic
        TestingFields t1 = [], t2 = []
        assertTrue LanguageUtils.hashCode(t1, ["a", "b"]) == LanguageUtils.hashCode(t2, ["a", "b"])
        assertTrue LanguageUtils.hashCode(t1, ["a", "@b"]) == LanguageUtils.hashCode(t2, ["a", "@b"])
        assertFalse LanguageUtils.hashCode(t1, ["a", "b"]) == LanguageUtils.hashCode(t2, ["a", "@b"])
        t2.b = 0;
        assertTrue LanguageUtils.hashCode(t1, ["a", "b"]) == LanguageUtils.hashCode(t2, ["a", "b"])
        assertTrue LanguageUtils.hashCode(t1, ["a", "@b"]) == LanguageUtils.hashCode(t2, ["a", "@b"])
        assertEquals 0, LanguageUtils.hashCode(null, ["a", "@b"])
    }
    
    @Test
    public void testToString() {
        TestingFields t1 = [];
        assertEquals(LanguageUtils.toString(t1, ["a", "b"]), "TestingFields[a=6,b=-7]");
    }
}

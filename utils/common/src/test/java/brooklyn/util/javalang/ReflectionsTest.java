package brooklyn.util.javalang;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

public class ReflectionsTest {

    @Test
    public void testFindPublicMethodsOrderedBySuper() throws Exception {
        List<Method> methods = Reflections.findPublicMethodsOrderedBySuper(MySubClass.class);
        assertContainsInOrder(methods, ImmutableList.of(
                MyInterface.class.getMethod("mymethod"), 
                MySuperClass.class.getMethod("mymethod"), 
                MySubClass.class.getMethod("mymethod")));
        assertNoDuplicates(methods);
    }
    
    @Test
    public void testFindPublicFieldsOrdereBySuper() throws Exception {
        List<Field> fields = Reflections.findPublicFieldsOrderedBySuper(MySubClass.class);
        assertContainsInOrder(fields, ImmutableList.of(
                MyInterface.class.getField("MY_FIELD"), 
                MySuperClass.class.getField("MY_FIELD"), 
                MySubClass.class.getField("MY_FIELD")));
        assertNoDuplicates(fields);
    }
    
    public static interface MyInterface {
        public static final int MY_FIELD = 0;
        public void mymethod();
    }
    public static class MySuperClass implements MyInterface {
        public static final int MY_FIELD = 0;
        
        @Override public void mymethod() {}
    }
    public static class MySubClass extends MySuperClass implements MyInterface {
        public static final int MY_FIELD = 0;
        @Override public void mymethod() {}
    }
    
    private void assertContainsInOrder(List<?> actual, List<?> subsetExpected) {
        int lastIndex = -1;
        for (Object e : subsetExpected) {
            int index = actual.indexOf(e);
            assertTrue(index >= 0 && index > lastIndex, "actual="+actual);
            lastIndex = index;
        }
    }
    
    private void assertNoDuplicates(List<?> actual) {
        assertEquals(actual.size(), Sets.newLinkedHashSet(actual).size(), "actual="+actual);
    }
    
    @SuppressWarnings("deprecation")
    @Test(enabled=false)
    public void testGetCaller() {
        Assert.assertEquals(Reflections.getCaller().getClassName(), ReflectionsTest.class.getName());
    }
    
    public static class CI1 {
        public static String m1(String x, int y) {
            return x+y;
        }
        public static String m1(String x, int y0, int y1, int ...yy) {
            int Y = y0 + y1;;
            for (int yi: yy) Y += yi;
            return x+Y;
        }
    }

    @Test
    public void testTypesMatch() throws Exception {
        Assert.assertTrue(Reflections.typesMatch(new Object[] { 3 }, new Class[] { Integer.class } ));
        Assert.assertTrue(Reflections.typesMatch(new Object[] { 3 }, new Class[] { int.class } ), "auto-boxing failure");
    }
    
    @Test
    public void testInvocation() throws Exception {
        Method m = CI1.class.getMethod("m1", String.class, int.class, int.class, int[].class);
        Assert.assertEquals(m.invoke(null, "hello", 1, 2, new int[] { 3, 4}), "hello10");
        
        Assert.assertEquals(Reflections.invokeMethodWithArgs(CI1.class, "m1", Arrays.<Object>asList("hello", 3)).get(), "hello3");
        Assert.assertEquals(Reflections.invokeMethodWithArgs(CI1.class, "m1", Arrays.<Object>asList("hello", 3, 4, 5)).get(), "hello12");
    }
}

package brooklyn.util.javalang;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

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
}

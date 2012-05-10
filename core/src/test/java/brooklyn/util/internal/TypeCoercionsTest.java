package brooklyn.util.internal;

import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.Set;

import org.codehaus.groovy.runtime.GStringImpl;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.flags.TypeCoercions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class TypeCoercionsTest {

    @Test
    public void testCoerceCharSequenceToString() {
        assertEquals(TypeCoercions.coerce(new StringBuilder("abc"), String.class), (String)"abc");
        assertEquals(TypeCoercions.coerce(new GStringImpl(new Object[0], new String[0]), String.class), (String)"");
    }
    
    @Test
    public void testCoerceStringToPrimitive() {
        assertEquals(TypeCoercions.coerce("1", Character.class), (Character)'1');
        assertEquals(TypeCoercions.coerce("1", Short.class), (Short)((short)1));
        assertEquals(TypeCoercions.coerce("1", Integer.class), (Integer)1);
        assertEquals(TypeCoercions.coerce("1", Long.class), (Long)1l);
        assertEquals(TypeCoercions.coerce("1", Float.class), (Float)1f);
        assertEquals(TypeCoercions.coerce("1", Double.class), (Double)1d);
        assertEquals(TypeCoercions.coerce("true", Boolean.class), (Boolean)true);
        
        assertEquals(TypeCoercions.coerce("1", char.class), (Character)'1');
        assertEquals(TypeCoercions.coerce("1", short.class), (Short)((short)1));
        assertEquals(TypeCoercions.coerce("1", int.class), (Integer)1);
        assertEquals(TypeCoercions.coerce("1", long.class), (Long)1l);
        assertEquals(TypeCoercions.coerce("1", float.class), (Float)1f);
        assertEquals(TypeCoercions.coerce("1", double.class), (Double)1d);
        assertEquals(TypeCoercions.coerce("true", boolean.class), (Boolean)true);
    }

    @Test
    public void testCoercePrimitivesToSameType() {
        assertEquals(TypeCoercions.coerce('1', Character.class), (Character)'1');
        assertEquals(TypeCoercions.coerce((short)1, Short.class), (Short)((short)1));
        assertEquals(TypeCoercions.coerce(1, Integer.class), (Integer)1);
        assertEquals(TypeCoercions.coerce(1l, Long.class), (Long)1l);
        assertEquals(TypeCoercions.coerce(1f, Float.class), (Float)1f);
        assertEquals(TypeCoercions.coerce(1d, Double.class), (Double)1d);
        assertEquals(TypeCoercions.coerce(true, Boolean.class), (Boolean)true);
    }
    
    @Test
    public void testListToSetCoercion() {
        Set<?> s = TypeCoercions.coerce(ImmutableList.of(1), Set.class);
        Assert.assertEquals(s, ImmutableSet.of(1));
    }
    
    @Test
    public void testSetToListCoercion() {
        List<?> s = TypeCoercions.coerce(ImmutableSet.of(1), List.class);
        Assert.assertEquals(s, ImmutableList.of(1));
    }

    @Test
    public void testAs() {
        Integer x = TypeCoercions.coerce(new WithAs("3"), Integer.class);
        Assert.assertEquals(x, (Integer)3);
    }

    @Test
    public void testFrom() {
        WithFrom x = TypeCoercions.coerce("3", WithFrom.class);
        Assert.assertEquals(x.value, 3);
    }

    public static class WithAs {
        String value;
        public WithAs(Object x) { value = ""+x; }
        public Integer asInteger() {
            return Integer.parseInt(value);
        }
    }

    public static class WithFrom {
        int value;
        public static WithFrom fromString(String s) {
            WithFrom result = new WithFrom();
            result.value = Integer.parseInt(s);
            return result;
        }
    }

}

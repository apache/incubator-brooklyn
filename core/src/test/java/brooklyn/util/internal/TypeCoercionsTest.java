package brooklyn.util.internal;

import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.flags.TypeCoercions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class TypeCoercionsTest {

    @Test
    public void testCoerceStringToPrimitive() {
        assertEquals(TypeCoercions.stringToPrimitive("1", Character.class), (Character)'1');
        assertEquals(TypeCoercions.stringToPrimitive("1", Short.class), (Short)((short)1));
        assertEquals(TypeCoercions.stringToPrimitive("1", Integer.class), (Integer)1);
        assertEquals(TypeCoercions.stringToPrimitive("1", Long.class), (Long)1l);
        assertEquals(TypeCoercions.stringToPrimitive("1", Float.class), (Float)1f);
        assertEquals(TypeCoercions.stringToPrimitive("1", Double.class), (Double)1d);
        assertEquals(TypeCoercions.stringToPrimitive("true", Boolean.class), (Boolean)true);
        
        assertEquals(TypeCoercions.stringToPrimitive("1", char.class), (Character)'1');
        assertEquals(TypeCoercions.stringToPrimitive("1", short.class), (Short)((short)1));
        assertEquals(TypeCoercions.stringToPrimitive("1", int.class), (Integer)1);
        assertEquals(TypeCoercions.stringToPrimitive("1", long.class), (Long)1l);
        assertEquals(TypeCoercions.stringToPrimitive("1", float.class), (Float)1f);
        assertEquals(TypeCoercions.stringToPrimitive("1", double.class), (Double)1d);
        assertEquals(TypeCoercions.stringToPrimitive("true", boolean.class), (Boolean)true);
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
    
    @Test(enabled=false) // FIXME would be nice if this worked!
    public void testCoercePrimitiveToOtherCastablePrimitive() {
        assertEquals(TypeCoercions.coerce('1', Double.class), (Double)1d);
        assertEquals(TypeCoercions.coerce((short)1, Double.class), (Double)1d);
        assertEquals(TypeCoercions.coerce(1, Double.class), (Double)1d);
        assertEquals(TypeCoercions.coerce(1l, Double.class), (Double)1d);
        assertEquals(TypeCoercions.coerce(1f, Double.class), (Double)1d);
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

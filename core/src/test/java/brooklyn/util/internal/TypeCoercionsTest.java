package brooklyn.util.internal;

import java.util.List;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.flags.TypeCoercions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class TypeCoercionsTest {

    @Test
    public void testListToSetCoercion() {
        Set s = TypeCoercions.coerce(ImmutableList.of(1), Set.class);
        Assert.assertEquals(s.size(), 1);
        Assert.assertEquals(s.iterator().next(), 1);
    }
    
    @Test
    public void testSetToListCoercion() {
        List s = TypeCoercions.coerce(ImmutableSet.of(1), List.class);
        Assert.assertEquals(s.size(), 1);
        Assert.assertEquals(s.iterator().next(), 1);
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

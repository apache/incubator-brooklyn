/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
package brooklyn.util.internal;

import static org.testng.Assert.assertEquals;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.groovy.runtime.GStringImpl;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Lifecycle;
import brooklyn.util.flags.ClassCoercionException;
import brooklyn.util.flags.TypeCoercions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;

public class TypeCoercionsTest {

    @Test
    public void testCoerceCharSequenceToString() {
        assertEquals(TypeCoercions.coerce(new StringBuilder("abc"), String.class), "abc");
        assertEquals(TypeCoercions.coerce(new GStringImpl(new Object[0], new String[0]), String.class), "");
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
    public void testCastPrimitives() {
        assertEquals(TypeCoercions.coerce(1L, Character.class), (Character)(char)1);
        assertEquals(TypeCoercions.coerce(1L, Byte.class), (Byte)(byte)1);
        assertEquals(TypeCoercions.coerce(1L, Short.class), (Short)(short)1);
        assertEquals(TypeCoercions.coerce(1L, Integer.class), (Integer)1);
        assertEquals(TypeCoercions.coerce(1L, Long.class), (Long)(long)1);
        assertEquals(TypeCoercions.coerce(1L, Float.class), (Float)(float)1);
        assertEquals(TypeCoercions.coerce(1L, Double.class), (Double)(double)1);
        
        assertEquals(TypeCoercions.coerce(1L, char.class), (Character)(char)1);
        assertEquals(TypeCoercions.coerce(1L, byte.class), (Byte)(byte)1);
        assertEquals(TypeCoercions.coerce(1L, short.class), (Short)(short)1);
        assertEquals(TypeCoercions.coerce(1L, int.class), (Integer)1);
        assertEquals(TypeCoercions.coerce(1L, long.class), (Long)(long)1);
        assertEquals(TypeCoercions.coerce(1L, float.class), (Float)(float)1);
        assertEquals(TypeCoercions.coerce(1L, double.class), (Double)(double)1);
        
        assertEquals(TypeCoercions.coerce((char)1, Integer.class), (Integer)1);
        assertEquals(TypeCoercions.coerce((byte)1, Integer.class), (Integer)1);
        assertEquals(TypeCoercions.coerce((short)1, Integer.class), (Integer)1);
        assertEquals(TypeCoercions.coerce((int)1, Integer.class), (Integer)1);
        assertEquals(TypeCoercions.coerce((long)1, Integer.class), (Integer)1);
        assertEquals(TypeCoercions.coerce((float)1, Integer.class), (Integer)1);
        assertEquals(TypeCoercions.coerce((double)1, Integer.class), (Integer)1);
        
    }
    
    @Test
    public void testCastToNumericPrimitives() {
        assertEquals(TypeCoercions.coerce(BigInteger.ONE, Integer.class), (Integer)1);
        assertEquals(TypeCoercions.coerce(BigInteger.ONE, int.class), (Integer)1);
        assertEquals(TypeCoercions.coerce(BigInteger.valueOf(Long.MAX_VALUE), Long.class), (Long)Long.MAX_VALUE);
        assertEquals(TypeCoercions.coerce(BigInteger.valueOf(Long.MAX_VALUE), long.class), (Long)Long.MAX_VALUE);
        
        assertEquals(TypeCoercions.coerce(BigDecimal.valueOf(0.5), Double.class), 0.5d, 0.00001d);
        assertEquals(TypeCoercions.coerce(BigDecimal.valueOf(0.5), double.class), 0.5d, 0.00001d);
    }

    @Test
    public void testCoerceStringToEnum() {
        assertEquals(TypeCoercions.coerce("STARTING", Lifecycle.class), Lifecycle.STARTING);
        assertEquals(TypeCoercions.coerce("Starting", Lifecycle.class), Lifecycle.STARTING);
        assertEquals(TypeCoercions.coerce("starting", Lifecycle.class), Lifecycle.STARTING);
        
        assertEquals(TypeCoercions.coerce("LOWERCASE", PerverseEnum.class), PerverseEnum.lowercase);
        assertEquals(TypeCoercions.coerce("CAMELCASE", PerverseEnum.class), PerverseEnum.camelCase);
        assertEquals(TypeCoercions.coerce("upper", PerverseEnum.class), PerverseEnum.UPPER);
        assertEquals(TypeCoercions.coerce("upper_with_underscore", PerverseEnum.class), PerverseEnum.UPPER_WITH_UNDERSCORE);
        assertEquals(TypeCoercions.coerce("LOWER_WITH_UNDERSCORE", PerverseEnum.class), PerverseEnum.lower_with_underscore);
    }
    public static enum PerverseEnum {
        lowercase,
        camelCase,
        UPPER,
        UPPER_WITH_UNDERSCORE,
        lower_with_underscore;
    }
    
    @Test(expectedExceptions = ClassCoercionException.class)
    public void testCoerceStringToEnumFailure() {
        TypeCoercions.coerce("scrambled-eggs", Lifecycle.class);
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
    public void testStringToListCoercion() {
        List<?> s = TypeCoercions.coerce("a,b,c", List.class);
        Assert.assertEquals(s, ImmutableList.of("a", "b", "c"));
    }

    @Test
    public void testStringToMapCoercion() {
        Map s = TypeCoercions.coerce("a=1,b=2,c=3", Map.class);
        Assert.assertEquals(s, ImmutableMap.of("a", "1", "b", "2", "c", "3"));
    }

    @Test
    public void testJsonStringToMapCoercion() {
        Map s = TypeCoercions.coerce("{ \"a\" : \"1\", \"b\" : \"2\", \"c\" : \"3\" }", Map.class);
        Assert.assertEquals(s, ImmutableMap.of("a", "1", "b", "2", "c", "3"));
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

    @Test
    public void testCoerceStringToNumber() {
        assertEquals(TypeCoercions.coerce("1", Number.class), (Number) Double.valueOf(1));
        assertEquals(TypeCoercions.coerce("1.0", Number.class), (Number) Double.valueOf(1.0));
    }

    @Test(expectedExceptions = ClassCoercionException.class)
    public void testInvalidCoercionThrowsClassCoercionException() {
        TypeCoercions.coerce(new Object(), TypeToken.of(Integer.class));
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

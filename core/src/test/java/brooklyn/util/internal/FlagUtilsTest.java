/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.trait.Configurable;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class FlagUtilsTest {

	public static final Logger log = LoggerFactory.getLogger(FlagUtilsTest.class);
	
	@Test
	public void testGetAllFields() {
		log.info("types {}", FlagUtils.getAllAssignableTypes(Baz.class));
		assertEquals(FlagUtils.getAllAssignableTypes(Baz.class), ImmutableList.of(Baz.class, Foo.class, Bar.class));
		List<Field> fs = FlagUtils.getAllFields(Baz.class);
		for (Field f : fs) {
		    log.info("field {}    {}", f.getName(), f);
		}
		List<String> fsn = ImmutableList.copyOf(Iterables.transform(fs, new Function<Field, String>() {
            @Override public String apply(Field f) {
                return f.getName();
            }}));
		assertTrue(fsn.indexOf("A") >= 0);
		assertTrue(fsn.indexOf("w") > fsn.indexOf("A")); 
		assertTrue(fsn.indexOf("x") > fsn.indexOf("A") );
		assertTrue(fsn.indexOf("yNotY") > fsn.indexOf("A")); 
		assertTrue(fsn.indexOf("Z") > fsn.indexOf("yNotY") );
	}	
	
    @Test
    public void testSetFieldsFromFlags() {
        Foo f = new Foo();
		Map<?,?> m = MutableMap.of("w", 3, "x", 1, "y", 7, "z", 9);
        Map<?, ?> unused = FlagUtils.setFieldsFromFlags(m, f);
		assertEquals(f.w, 3);
        assertEquals(f.x, 1);
        assertEquals(f.yNotY, 7);
        assertEquals(unused, ImmutableMap.of("z", 9));
		Map<?,?> m2 = FlagUtils.getFieldsWithValues(f);
		m.remove("z");
		assertEquals(m2, m);
    }
    
    @Test
    public void testCollectionCoercionOnSetFromFlags() {
        WithSpecialFieldTypes s = new WithSpecialFieldTypes();
        Map<?,?> m = ImmutableMap.of("set", ImmutableSet.of(1));
        FlagUtils.setFieldsFromFlags(m, s);
        assertEquals(s.set, ImmutableSet.of(1));
    }

    @Test
    public void testInetAddressCoercionOnSetFromFlags() {
        WithSpecialFieldTypes s = new WithSpecialFieldTypes();
        Map<?,?> m = ImmutableMap.of("inet", "127.0.0.1");
        FlagUtils.setFieldsFromFlags(m, s);
        assertEquals(s.inet.getHostAddress(), "127.0.0.1");
    }

    @Test
    public void testNonImmutableField() {
        Foo f = new Foo();
        FlagUtils.setFieldsFromFlags(ImmutableMap.of("w", 8), f);
        assertEquals(f.w, 8);
        FlagUtils.setFieldsFromFlags(ImmutableMap.of("w", 9), f);
        assertEquals(f.w, 9);
    }

    @Test
    public void testImmutableIntField() {
        Foo f = new Foo();
        FlagUtils.setFieldsFromFlags(ImmutableMap.of("x", 8), f);
        assertEquals(f.x, 8);
        boolean succeededWhenShouldntHave = false; 
        try {
            FlagUtils.setFieldsFromFlags(ImmutableMap.of("x", 9), f);
            succeededWhenShouldntHave = true;
        } catch (IllegalStateException e) {
            //expected
        }
        assertFalse(succeededWhenShouldntHave);
        assertEquals(f.x, 8);
    }

    @Test
    public void testImmutableObjectField() {
        WithImmutableNonNullableObject o = new WithImmutableNonNullableObject();
        FlagUtils.setFieldsFromFlags(ImmutableMap.of("a", "a", "b", "b"), o);
        assertEquals(o.a, "a");
        assertEquals(o.b, "b");
        
        FlagUtils.setFieldsFromFlags(ImmutableMap.of("a", "a2"), o);
        assertEquals(o.a, "a2");
        
        boolean succeededWhenShouldntHave = false;
        try {
            FlagUtils.setFieldsFromFlags(ImmutableMap.of("b", "b2"), o);
            succeededWhenShouldntHave = true;
        } catch (IllegalStateException e) {
            //expected
        }
        assertFalse(succeededWhenShouldntHave);
        assertEquals(o.b, "b");
    }

    @Test
    public void testNonNullable() {
        WithImmutableNonNullableObject o = new WithImmutableNonNullableObject();
        //allowed
        FlagUtils.setFieldsFromFlags(MutableMap.of("a", null), o);
        assertEquals(o.a, null);
        assertEquals(o.b, null);
        //not allowed
        boolean succeededWhenShouldntHave = false;
        try {
            FlagUtils.setFieldsFromFlags(MutableMap.of("b", null), o);
            succeededWhenShouldntHave = true;
        } catch (IllegalArgumentException e) {
            //expected
        }
        assertFalse(succeededWhenShouldntHave);
        assertEquals(o.b, null);
    }
    
    @Test
    public void testGetAnnotatedFields() throws Exception {
        Map<Field, SetFromFlag> fm = FlagUtils.getAnnotatedFields(WithImmutableNonNullableObject.class);
        assertEquals(fm.keySet().size(), 2);
        assertTrue(fm.get(WithImmutableNonNullableObject.class.getDeclaredField("b")).immutable());
    }

    @Test
    public void testCheckRequired() {
        WithImmutableNonNullableObject f = new WithImmutableNonNullableObject();
        FlagUtils.setFieldsFromFlags(ImmutableMap.of("a", "a is a"), f);
        assertEquals(f.a, "a is a");
        assertEquals(f.b, null);
        int exceptions = 0;
        try {
            FlagUtils.checkRequiredFields(f);
        } catch (IllegalStateException e) {
            exceptions++;
        }
        assertEquals(exceptions, 1);
    }

    @Test
    public void testSetConfigKeys() {
        FooCK f = new FooCK();
        Map<?,?> unused = FlagUtils.setFieldsFromFlags(ImmutableMap.of("f1", 9, "ck1", "do-set", "ck2", "dont-set", "c3", "do-set"), f);
        assertEquals(f.bag.get(FooCK.CK1), "do-set");
        assertEquals(f.bag.get(FooCK.CK3), "do-set");
        assertEquals(f.f1, 9);
        assertEquals(f.bag.containsKey(FooCK.CK2), false);
        assertEquals(unused, ImmutableMap.of("ck2", "dont-set"));
    }
    
    @Test
    public void testSetAllConfigKeys() {
        FooCK f = new FooCK();
        Map<?,?> unused = FlagUtils.setAllConfigKeys(ImmutableMap.of("f1", 9, "ck1", "do-set", "ck2", "do-set-2", "c3", "do-set"), f, true);
        assertEquals(f.bag.get(FooCK.CK1), "do-set");
        assertEquals(f.bag.get(FooCK.CK3), "do-set");
        assertEquals(f.bag.containsKey(FooCK.CK2), true);
        assertEquals(f.bag.get(FooCK.CK2), "do-set-2");
        assertEquals(unused, ImmutableMap.of("f1", 9));
    }

    @Test
    public void testSetFromConfigKeys() {
        FooCK f = new FooCK();
        Map<?, ?> unused = FlagUtils.setFieldsFromFlags(ImmutableMap.of(new BasicConfigKey<Integer>(Integer.class, "f1"), 9, "ck1", "do-set", "ck2", "dont-set"), f);
        assertEquals(f.bag.get(FooCK.CK1), "do-set");
        assertEquals(f.f1, 9);
        assertEquals(f.bag.containsKey(FooCK.CK2), false);
        assertEquals(unused, ImmutableMap.of("ck2", "dont-set"));
    }

    public static class Foo {
    	@SetFromFlag
    	int w;
    	
    	@SetFromFlag(immutable=true)
    	private int x;
    	
    	@SetFromFlag("y")
    	public int yNotY;
    }
    
    public static interface Bar {
    	static final String Z = "myzval";
    }
    
    public static class Baz extends Foo implements Bar {
    	@SuppressWarnings("unused")  //inspected by reflection
        private static int A;
    }
    
    public static class WithImmutableNonNullableObject {
        @SetFromFlag
        Object a;
        @SetFromFlag(immutable=true, nullable=false)
        public Object b;
    }
    
    public static class WithSpecialFieldTypes {
        @SetFromFlag Set<?> set;
        @SetFromFlag InetAddress inet;
    }
    
    public static class FooCK implements Configurable {
        @SetFromFlag
        public static ConfigKey<String> CK1 = ConfigKeys.newStringConfigKey("ck1");
        
        public static ConfigKey<String> CK2 = ConfigKeys.newStringConfigKey("ck2");

        @SetFromFlag("c3")
        public static ConfigKey<String> CK3 = ConfigKeys.newStringConfigKey("ck3");

        @SetFromFlag
        int f1;
        
        ConfigBag bag = new ConfigBag();
        public <T> T setConfig(ConfigKey<T> key, T val) {
            return bag.put(key, val);
        }
    }
}
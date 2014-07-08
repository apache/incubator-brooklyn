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
import com.google.common.collect.Lists;
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
    
    public static class CI1 {
        public final List<Object> constructorArgs;
        
        public CI1() {
            constructorArgs = ImmutableList.of();
        }
        public CI1(String x, int y) {
            constructorArgs = ImmutableList.<Object>of(x, y);
        }
        public CI1(String x, int y0, int y1, int ...yy) {
            constructorArgs = Lists.newArrayList();
            constructorArgs.addAll(ImmutableList.of(x, y0, y1));
            for (int yi: yy) constructorArgs.add((Integer)yi);
        }
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
    
    @Test
    public void testConstruction() throws Exception {
        Assert.assertEquals(Reflections.invokeConstructorWithArgs(CI1.class, new Object[] {"hello", 3}).get().constructorArgs, ImmutableList.of("hello", 3));
        Assert.assertEquals(Reflections.invokeConstructorWithArgs(CI1.class, new Object[] {"hello", 3, 4, 5}).get().constructorArgs, ImmutableList.of("hello", 3, 4, 5));
        Assert.assertFalse(Reflections.invokeConstructorWithArgs(CI1.class, new Object[] {"wrong", "args"}).isPresent());
    }

    interface I { };
    interface J extends I { };
    interface K extends I, J { };
    interface L { };
    interface M { };
    class A implements I { };
    class B extends A implements L { };
    class C extends B implements M, K { };
    
    @Test
    public void testGetAllInterfaces() throws Exception {
        Assert.assertEquals(Reflections.getAllInterfaces(A.class), ImmutableList.of(I.class));
        Assert.assertEquals(Reflections.getAllInterfaces(B.class), ImmutableList.of(L.class, I.class));
        Assert.assertEquals(Reflections.getAllInterfaces(C.class), ImmutableList.of(M.class, K.class, I.class, J.class, L.class));
    }

}

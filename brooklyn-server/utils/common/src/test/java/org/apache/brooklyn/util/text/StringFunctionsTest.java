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
package org.apache.brooklyn.util.text;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;

public class StringFunctionsTest {

    @Test
    public static void testPrepend() {
        Assert.assertEquals(StringFunctions.prepend("Hello ").apply("World"), "Hello World");
    }
    
    @Test
    public static void testFormatter() {
        Assert.assertEquals(StringFunctions.formatter("Hello %s").apply("World"), "Hello World");
    }
    
    @Test
    public static void testFormatterForArray() {
        Assert.assertEquals(StringFunctions.formatterForArray("Hello %s").apply(new Object[] { "World" }), "Hello World");
        Assert.assertEquals(StringFunctions.formatterForArray("Hello").apply(new Object[0]), "Hello");
    }
    
    @Test
    public static void testFormatterForArrayMulti() {
        Assert.assertEquals(StringFunctions.formatterForArray("1 %s 2 %s").apply(new Object[] {"val1", "val2"}), "1 val1 2 val2");
    }

    @Test
    public static void testFormatterForIterable() {
        Assert.assertEquals(StringFunctions.formatterForIterable("Hello %s").apply(ImmutableList.of("World")), "Hello World");
        Assert.assertEquals(StringFunctions.formatterForIterable("Hello").apply(ImmutableList.of()), "Hello");
        Assert.assertEquals(StringFunctions.formatterForIterable("Hello").apply(null), "Hello");
    }
    
    @Test
    public static void testSurround() {
        Assert.assertEquals(StringFunctions.surround("goodbye ", " world").apply("cruel"), "goodbye cruel world");
    }
    
    @Test
    public static void testLowerCase() {
        Assert.assertEquals(StringFunctions.toLowerCase().apply("Hello World"), "hello world");
    }
    
    @Test
    public static void testUpperCase() {
        Assert.assertEquals(StringFunctions.toUpperCase().apply("Hello World"), "HELLO WORLD");
    }
    
    @Test
    public static void testConvertCase() {
        Assert.assertEquals(StringFunctions.convertCase(CaseFormat.UPPER_UNDERSCORE, CaseFormat.UPPER_CAMEL).apply("HELLO_WORLD"), "HelloWorld");
    }
    
    @Test
    public static void testJoiner() {
        Assert.assertEquals(StringFunctions.joiner(",").apply(ImmutableList.of("a", "b", "c")), "a,b,c");
    }
    
    @Test
    public static void testJoinerForArray() {
        Assert.assertEquals(StringFunctions.joinerForArray(",").apply(new Object[] {"a", "b", "c"}), "a,b,c");
    }
    
    @Test
    public static void testLength() {
        Assert.assertEquals(StringFunctions.length().apply("abc"), (Integer)3);
    }
    
    @Test
    public static void testTrim() {
        Assert.assertEquals(StringFunctions.trim().apply(" abc "), "abc");
    }
}

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
package brooklyn.util.text;

import org.testng.Assert;
import org.testng.annotations.Test;

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
    
}

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
package brooklyn.util.guava;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.guava.IfFunctions.IfFunctionBuilder;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.base.Suppliers;

public class IfFunctionsTest {

    @Test
    public void testCommonUsage() {
        checkTF(IfFunctions.ifEquals(false).value("F").ifEquals(true).value("T").defaultValue("?").build(), "?");
    }

    @Test
    public void testNoBuilder() {
        checkTF(IfFunctions.ifEquals(false).value("F").ifEquals(true).value("T").defaultValue("?"), "?");
    }
    
    @Test
    public void testPredicateAndSupplier() {
        checkTF(IfFunctions.ifPredicate(Predicates.equalTo(false)).get(Suppliers.ofInstance("F"))
            .ifEquals(true).value("T").defaultGet(Suppliers.ofInstance("?")).build(), "?");
    }

    @Test
    public void testNoDefault() {
        checkTF(IfFunctions.ifEquals(false).value("F").ifEquals(true).value("T").build(), null);
    }

    @Test
    public void testNotEqual() {
        checkTF(IfFunctions.ifNotEquals(false).value("T").defaultValue("F").build(), "T");
    }

    @Test
    public void testFunction() {
        checkTF(IfFunctions.ifNotEquals((Boolean)null).apply(new Function<Boolean, String>() {
            @Override
            public String apply(Boolean input) {
                return input.toString().toUpperCase().substring(0, 1);
            }
        }).defaultValue("?"), "?");
    }

    @Test
    public void testWithCast() {
        Function<Boolean, String> f = IfFunctions.ifEquals(false).value("F").ifEquals(true).value("T").defaultValue("?").build();
        checkTF(f, "?");
    }

    @Test
    public void testWithoutCast() {
        Function<Boolean, String> f = IfFunctions.newInstance(Boolean.class, String.class).ifEquals(false).value("F").ifEquals(true).value("T").defaultValue("?").build();
        checkTF(f, "?");
    }

    @Test
    public void testSupportsReplace() {
        checkTF(IfFunctions.ifEquals(false).value("false").ifEquals(false).value("F").ifEquals(true).value("T").defaultValue("?").build(), "?");
    }

    @Test
    public void testIsImmutableAndSupportsReplace() {
        IfFunctionBuilder<Boolean, String> f = IfFunctions.ifEquals(false).value("F").ifEquals(true).value("T").defaultValue("?");
        IfFunctionBuilder<Boolean, String> f2 = f.ifEquals(false).value("false").defaultValue("X");
        IfFunctionBuilder<Boolean, String> f3 = f2.ifEquals(false).value("F");
        checkTF(f, "?");
        checkTF(f3, "X");
        Assert.assertEquals(f2.apply(false), "false");
    }

    static void checkTF(Function<Boolean, String> f, Object defaultValue) {
        Assert.assertEquals(f.apply(true), "T");
        Assert.assertEquals(f.apply(false), "F");
        Assert.assertEquals(f.apply(null), defaultValue);
    }
    
}

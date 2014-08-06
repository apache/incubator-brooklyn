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

import brooklyn.util.guava.WhenFunctions.WhenFunctionBuilder;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.base.Suppliers;

public class WhenFunctionsTest {

    @Test
    public void testWhen() {
        checkTF(WhenFunctions.when(false).value("F").when(true).value("T").defaultValue("?").build(), "?");
    }

    @Test
    public void testWhenNoBuilder() {
        checkTF(WhenFunctions.when(false).value("F").when(true).value("T").defaultValue("?"), "?");
    }
    
    @Test
    public void testWhenPredicateAndSupplier() {
        checkTF(WhenFunctions.when(Predicates.equalTo(false)).value(Suppliers.ofInstance("F"))
            .when(true).value("T").defaultValue(Suppliers.ofInstance("?")).build(), "?");
    }

    @Test
    public void testWhenTwoArgs() {
        checkTF(WhenFunctions.when(Predicates.equalTo(false), "F").when(Predicates.equalTo(true), "T").defaultValue("?").build(), "?");
    }
    
    @Test
    public void testWhenNoDefault() {
        checkTF(WhenFunctions.when(false).value("F").when(true).value("T").build(), null);
    }

    @Test
    public void testWhenWithCast() {
        Function<Boolean, String> f = WhenFunctions.<Boolean,String>when(false).value("F").when(true).value("T").defaultValue("?").build();
        checkTF(f, "?");
    }

    @Test
    public void testWhenWithoutCast() {
        Function<Boolean, String> f = WhenFunctions.newInstance(Boolean.class, String.class).when(false).value("F").when(true).value("T").defaultValue("?").build();
        checkTF(f, "?");
    }

    @Test
    public void testWhenSupportsReplace() {
        checkTF(WhenFunctions.when(false).value("false").when(false).value("F").when(true).value("T").defaultValue("?").build(), "?");
    }

    @Test
    public void testWhenIsImmutableAndSupportsReplace() {
        WhenFunctionBuilder<Boolean, String> f = WhenFunctions.when(false).value("F").when(true).value("T").defaultValue("?");
        WhenFunctionBuilder<Boolean, String> f2 = f.when(false).value("false").defaultValue("X");
        WhenFunctionBuilder<Boolean, String> f3 = f2.when(false).value("F");
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

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

import brooklyn.util.math.MathFunctions;

import com.google.common.base.Predicates;
import com.google.common.base.Suppliers;

public class FunctionalsTest {

    @Test
    public void testChain() {
        Assert.assertEquals(Functionals.chain(MathFunctions.plus(1), MathFunctions.times(2)).apply(3), (Integer)8);
        Assert.assertEquals(Functionals.chain(MathFunctions.times(2), MathFunctions.plus(1)).apply(3), (Integer)7);
    }

    @Test
    public void testIf() {
        IfFunctionsTest.checkTF(Functionals.ifEquals(false).value("F").ifEquals(true).value("T").defaultValue("?").build(), "?");
    }

    @Test
    public void testIfNoBuilder() {
        IfFunctionsTest.checkTF(Functionals.ifEquals(false).value("F").ifEquals(true).value("T").defaultValue("?"), "?");
    }
    
    @Test
    public void testIfPredicateAndSupplier() {
        IfFunctionsTest.checkTF(Functionals.ifPredicate(Predicates.equalTo(false)).get(Suppliers.ofInstance("F"))
            .ifEquals(true).value("T").defaultGet(Suppliers.ofInstance("?")).build(), "?");
    }

    @Test
    public void testIfNotEqual() {
        IfFunctionsTest.checkTF(Functionals.ifNotEquals(false).value("T").defaultValue("F").build(), "T");
    }

}

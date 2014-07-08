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
package io.brooklyn.camp.brooklyn.spi.dsl;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import io.brooklyn.camp.brooklyn.spi.dsl.parse.DslParser;
import io.brooklyn.camp.brooklyn.spi.dsl.parse.FunctionWithArgs;
import io.brooklyn.camp.brooklyn.spi.dsl.parse.QuotedString;

import java.util.Arrays;
import java.util.List;

import org.testng.annotations.Test;

import brooklyn.util.text.StringEscapes.JavaStringEscapes;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

@Test
public class DslParseTest {

    public void testParseString() {
        assertEquals(new DslParser("\"hello world\"").parse(), new QuotedString(JavaStringEscapes.wrapJavaString("hello world")));
    }

    public void testParseNoArgFunction() {
        Object fx = new DslParser("f()").parse();
        fx = Iterables.getOnlyElement( (List<?>)fx );
        assertEquals( ((FunctionWithArgs)fx).getFunction(), "f" );
        assertEquals( ((FunctionWithArgs)fx).getArgs(), ImmutableList.of());
    }
    
    public void testParseOneArgFunction() {
        Object fx = new DslParser("f(\"x\")").parse();
        fx = Iterables.getOnlyElement( (List<?>)fx );
        assertEquals( ((FunctionWithArgs)fx).getFunction(), "f" );
        assertEquals( ((FunctionWithArgs)fx).getArgs(), Arrays.asList(new QuotedString("\"x\"")) );
    }
    
    public void testParseMultiArgMultiTypeFunction() {
        // TODO Parsing "f(\"x\", 1)" fails, because it interprets 1 as a function rather than a number. Is that expected?
        Object fx = new DslParser("f(\"x\", \"y\")").parse();
        fx = Iterables.getOnlyElement( (List<?>)fx );
        assertEquals( ((FunctionWithArgs)fx).getFunction(), "f" );
        assertEquals( ((FunctionWithArgs)fx).getArgs(), ImmutableList.of(new QuotedString("\"x\""), new QuotedString("\"y\"")));
    }

    
    public void testParseFunctionChain() {
        Object fx = new DslParser("f(\"x\").g()").parse();
        assertTrue(((List<?>)fx).size() == 2, ""+fx);
        Object fx1 = ((List<?>)fx).get(0);
        Object fx2 = ((List<?>)fx).get(1);
        assertEquals( ((FunctionWithArgs)fx1).getFunction(), "f" );
        assertEquals( ((FunctionWithArgs)fx1).getArgs(), ImmutableList.of(new QuotedString("\"x\"")) );
        assertEquals( ((FunctionWithArgs)fx2).getFunction(), "g" );
        assertTrue( ((FunctionWithArgs)fx2).getArgs().isEmpty() );
    }
    

}

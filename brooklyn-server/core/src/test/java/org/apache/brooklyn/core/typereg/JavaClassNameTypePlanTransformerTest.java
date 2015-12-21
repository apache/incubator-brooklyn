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
package org.apache.brooklyn.core.typereg;

import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.core.test.BrooklynMgmtUnitTestSupport;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class JavaClassNameTypePlanTransformerTest extends BrooklynMgmtUnitTestSupport {

    public static class NoArg {
        public String name() { return "no-arg"; }
    }

    protected RegisteredType type;
    protected BrooklynTypePlanTransformer transformer;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        type = newNoArgRegisteredType(JavaClassNameTypePlanTransformer.FORMAT);
        transformer = newTransformer();
    }
    
    protected RegisteredType newNoArgRegisteredType(String format) {
        return RegisteredTypes.bean("no-arg", "1.0", new BasicTypeImplementationPlan(format, NoArg.class.getName()), null);
    }
    
    protected BrooklynTypePlanTransformer newTransformer() {
        BrooklynTypePlanTransformer xf = new JavaClassNameTypePlanTransformer();
        xf.setManagementContext(mgmt);
        return xf;
    }
    
    @Test
    public void testScoreJavaType() {
        double score = transformer.scoreForType(type, null);
        Assert.assertEquals(score, 1, 0.00001);
    }

    @Test
    public void testCreateJavaType() {
        Object obj = transformer.create(type, null);
        Assert.assertTrue(obj instanceof NoArg, "obj is "+obj);
        Assert.assertEquals(((NoArg)obj).name(), "no-arg");
    }

    @Test
    public void testScoreJavaTypeWithNullFormat() {
        type = newNoArgRegisteredType(null);
        double score = transformer.scoreForType(type, null);
        Assert.assertEquals(score, 0.1, 0.00001);
    }

    @Test
    public void testCreateJavaTypeWithNullFormat() {
        type = newNoArgRegisteredType(null);
        Object obj = transformer.create(type, null);
        Assert.assertTrue(obj instanceof NoArg, "obj is "+obj);
        Assert.assertEquals(((NoArg)obj).name(), "no-arg");
    }

    @Test
    public void testScoreJavaTypeWithOtherFormat() {
        type = newNoArgRegisteredType("crazy-format");
        double score = transformer.scoreForType(type, null);
        Assert.assertEquals(score, 0, 0.00001);
        // we don't test creation; it may or may not succeed, but with score 0 it shouldn't get invoked
    }

}

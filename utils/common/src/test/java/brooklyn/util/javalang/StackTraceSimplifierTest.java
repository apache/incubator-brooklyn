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

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.exceptions.Exceptions;

public class StackTraceSimplifierTest {

    @Test
    public void isStackTraceElementUsefulRejectsABlacklistedPackage() {
        StackTraceElement el = new StackTraceElement("groovy.lang.Foo", "bar", "groovy/lang/Foo.groovy", 42);
        Assert.assertFalse(StackTraceSimplifier.isStackTraceElementUseful(el));
    }

    @Test
    public void isStackTraceElementUsefulAcceptsANonBlacklistedPackage() {
        StackTraceElement el = new StackTraceElement(
            "brooklyn.util.task", "StackTraceSimplifierTest", "StackTraceSimplifierTest.groovy", 42);
        Assert.assertTrue(StackTraceSimplifier.isStackTraceElementUseful(el));
    }
    
    @Test
    public void cleanTestTrace() {
        RuntimeException t = StackTraceSimplifier.newInstance(StackTraceSimplifierTest.class.getName())
            .cleaned(new RuntimeException("sample"));
        // should exclude *this* class also
        Assert.assertTrue(t.getStackTrace()[0].getClassName().startsWith("org.testng"),
                "trace was: "+t.getStackTrace()[0]);
    }

    private int m1(int x) {
        int count = StackTraceSimplifier.getRecursiveCallCount();
        if (count>100) throw new RuntimeException("expected");
        if (x<=0) {
            return count;
        }
        return m2(x-1);
    }
    private int m2(int x) {
        if (x<=0) return -1;
        return m1(x-1);
    }
    @Test
    public void testIsRecursiveCallToSelf() {
        Assert.assertEquals(m1(1), -1);
        Assert.assertEquals(m2(1), 0);
        Assert.assertEquals(m1(2), 1);
        Assert.assertEquals(m2(2), -1);
        Assert.assertEquals(m1(3), -1);
        Assert.assertEquals(m1(4), 2);
        Assert.assertEquals(m1(20), 10);
        
        try {
            m1(500);
            Assert.fail("should have failed on recursive call");
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            if (!e.getMessage().equals("expected"))
                throw Exceptions.propagate(e);
        }
    }

}

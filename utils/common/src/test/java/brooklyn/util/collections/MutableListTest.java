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
package brooklyn.util.collections;

import java.util.Arrays;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class MutableListTest {

    public void testEqualsExact() {
        List<Object> a = MutableList.<Object>of("a", 1, "b", false);
        List<Object> b = MutableList.<Object>of("a", 1, "b", false);
        Assert.assertEquals(a, b);
    }
    
    public void testNotEqualsUnordered() {
        List<Object> a = MutableList.<Object>of("a", 1, "b", false);
        List<Object> b = MutableList.<Object>of("b", false, "a", 1);
        Assert.assertNotEquals(a, b);
    }

    public void testEqualsDifferentTypes() {
        List<Object> a = MutableList.<Object>of("a", 1, "b", false);
        List<Object> b = Arrays.<Object>asList("a", 1, "b", false);
        Assert.assertEquals(a, b);
        Assert.assertEquals(b, a);
    }

    public void testEqualsDifferentTypes2() {
        List<Object> a = MutableList.<Object>of("http");
        List<String> b = Arrays.<String>asList("http");
        Assert.assertEquals(a, b);
        Assert.assertEquals(b, a);
    }

}

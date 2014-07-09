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
package brooklyn.util.xstream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import org.testng.annotations.Test;

public class EnumCaseForgivingConverterTest {

    public enum MyEnum {
        FOO,
        BaR;
    }
    
    @Test
    public void testFindsCaseInsensitive() throws Exception {
        assertEquals(EnumCaseForgivingConverter.resolve(MyEnum.class, "FOO"), MyEnum.FOO);
        assertEquals(EnumCaseForgivingConverter.resolve(MyEnum.class, "foo"), MyEnum.FOO);
        assertEquals(EnumCaseForgivingConverter.resolve(MyEnum.class, "Foo"), MyEnum.FOO);
        assertEquals(EnumCaseForgivingConverter.resolve(MyEnum.class, "BAR"), MyEnum.BaR);
        assertEquals(EnumCaseForgivingConverter.resolve(MyEnum.class, "bar"), MyEnum.BaR);
        assertEquals(EnumCaseForgivingConverter.resolve(MyEnum.class, "Bar"), MyEnum.BaR);
    }
    
    @Test
    public void testFailsIfNoMatch() throws Exception {
        try {
            assertEquals(EnumCaseForgivingConverter.resolve(MyEnum.class, "DoesNotExist"), MyEnum.BaR);
            fail();
        } catch (IllegalArgumentException e) {
            if (!e.toString().matches(".*No enum.*MyEnum.DOESNOTEXIST")) throw e;
        }
    }
}

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

import java.net.UnknownHostException;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.thoughtworks.xstream.XStream;

@Test
public class ImmutableListConverterTest extends ConverterTestFixture {

    protected void registerConverters(XStream xstream) {
        super.registerConverters(xstream);
        xstream.aliasType("ImmutableList", ImmutableList.class);
        xstream.registerConverter(new ImmutableListConverter(xstream.getMapper()));
    }

    @Test
    public void testImmutableEmptyList() throws UnknownHostException {
        assertX(ImmutableList.of(), "<ImmutableList/>");
    }

    @Test
    public void testImmutableSingletonDoubleList() throws UnknownHostException {
        assertX(ImmutableList.of(1.2d), "<ImmutableList>\n  <double>1.2</double>\n</ImmutableList>");
    }

    @Test
    public void testImmutableTwoValStringList() throws UnknownHostException {
        assertX(ImmutableList.of("a","b"), "<ImmutableList>\n  <string>a</string>\n  <string>b</string>\n</ImmutableList>");
    }

    @Test
    public void testImmutableEmptyListStaysImmutable() throws UnknownHostException {
        Object x = assertX(ImmutableList.of(), "<ImmutableList/>");
        Assert.assertTrue(x instanceof ImmutableList);
    }

}

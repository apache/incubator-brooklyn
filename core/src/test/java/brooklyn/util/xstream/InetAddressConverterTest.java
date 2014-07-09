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

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.testng.annotations.Test;

import com.thoughtworks.xstream.XStream;

@Test
public class InetAddressConverterTest extends ConverterTestFixture {

    protected void registerConverters(XStream xstream) {
        super.registerConverters(xstream);
        xstream.registerConverter(new Inet4AddressConverter());
    }

    public void testFoo1234() throws UnknownHostException {
        assertX(InetAddress.getByAddress("foo", new byte[] { 1, 2, 3, 4 }), 
                "<java.net.Inet4Address>foo/1.2.3.4</java.net.Inet4Address>");
    }
    
}

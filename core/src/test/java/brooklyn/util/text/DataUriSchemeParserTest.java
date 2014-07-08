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
package brooklyn.util.text;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.bouncycastle.util.encoders.Base64;
import org.testng.Assert;
import org.testng.annotations.Test;

public class DataUriSchemeParserTest {

    @Test
    public void testSimple() {
        Assert.assertEquals(new DataUriSchemeParser("data:,hello").parse().getDataAsString(), "hello");
        Assert.assertEquals(DataUriSchemeParser.toString("data:,hello"), "hello");
    }

    @Test
    public void testMimeType() throws UnsupportedEncodingException {
        DataUriSchemeParser p = new DataUriSchemeParser("data:application/json,"+URLEncoder.encode("{ }", "US-ASCII")).parse();
        Assert.assertEquals(p.getMimeType(), "application/json");
        Assert.assertEquals(p.getData(), "{ }".getBytes());
    }

    @Test
    public void testBase64() {
        Assert.assertEquals(DataUriSchemeParser.toString(
                "data:;base64,"+new String(Base64.encode("hello".getBytes()))), 
            "hello");
    }

    // TODO test pictures, etc
    
}

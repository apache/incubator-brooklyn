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
package brooklyn.util.net;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import com.google.common.net.HostAndPort;

public class UserAndHostAndPortTest {

    @Test
    public void testFromParts() throws Exception {
        assertIt(UserAndHostAndPort.fromParts("myuser", "myhost", 1234), "myuser", HostAndPort.fromParts("myhost", 1234));
    }
    
    @Test
    public void testFromString() throws Exception {
        assertIt(UserAndHostAndPort.fromString("myuser@myhost:1234"), "myuser", HostAndPort.fromParts("myhost", 1234));
        assertIt(UserAndHostAndPort.fromString("myuser @ myhost:1234"), "myuser", HostAndPort.fromParts("myhost", 1234));
        assertIt(UserAndHostAndPort.fromString("myuser @ myhost"), "myuser", HostAndPort.fromString("myhost"));
    }
    
    private void assertIt(UserAndHostAndPort actual, String user, HostAndPort hostAndPort) {
        assertEquals(actual.getUser(), user);
        assertEquals(actual.getHostAndPort(), hostAndPort);
        if (hostAndPort.hasPort()) {
            assertEquals(actual.toString(), user + "@" + hostAndPort.getHostText() + ":" + hostAndPort.getPort());
        } else {
            assertEquals(actual.toString(), user + "@" + hostAndPort.getHostText());
        }
    }
}

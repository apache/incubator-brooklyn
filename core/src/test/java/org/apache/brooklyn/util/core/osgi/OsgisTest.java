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
package org.apache.brooklyn.util.core.osgi;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import org.apache.brooklyn.util.core.osgi.Osgis;
import org.apache.brooklyn.util.core.osgi.Osgis.VersionedName;
import org.osgi.framework.Version;
import org.testng.annotations.Test;

public class OsgisTest {

    @Test
    public void testParseOsgiIdentifier() throws Exception {
        assertEquals(Osgis.parseOsgiIdentifier("a.b").get(), new VersionedName("a.b", null));
        assertEquals(Osgis.parseOsgiIdentifier("a.b:0.1.2").get(), new VersionedName("a.b", Version.parseVersion("0.1.2")));
        assertEquals(Osgis.parseOsgiIdentifier("a.b:0.0.0.SNAPSHOT").get(), new VersionedName("a.b", Version.parseVersion("0.0.0.SNAPSHOT")));
        assertFalse(Osgis.parseOsgiIdentifier("a.b:0.notanumber.2").isPresent()); // invalid version
        assertFalse(Osgis.parseOsgiIdentifier("a.b:0.1.2:3.4.5").isPresent());    // too many colons
        assertFalse(Osgis.parseOsgiIdentifier("a.b:0.0.0_SNAPSHOT").isPresent()); // invalid version
        assertFalse(Osgis.parseOsgiIdentifier("").isPresent());
    }
}

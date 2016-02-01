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
package org.apache.brooklyn.rest.security;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class PasswordHasherTest {

    @Test
    public void testHashSha256() throws Exception {
        // Note: expected hash values generated externally:
        // echo -n mysaltmypassword | openssl dgst -sha256

        assertEquals(PasswordHasher.sha256("mysalt", "mypassword"), "d02878b06efa88579cd84d9e50b211c0a7caa92cf243bad1622c66081f7e2692");
        assertEquals(PasswordHasher.sha256("", "mypassword"), "89e01536ac207279409d4de1e5253e01f4a1769e696db0d6062ca9b8f56767c8");
        assertEquals(PasswordHasher.sha256(null, "mypassword"), "89e01536ac207279409d4de1e5253e01f4a1769e696db0d6062ca9b8f56767c8");
    }

}

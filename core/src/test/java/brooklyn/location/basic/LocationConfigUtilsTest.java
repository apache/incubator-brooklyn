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
package brooklyn.location.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import brooklyn.util.config.ConfigBag;

public class LocationConfigUtilsTest {

    // set these system properties differently if needed to fix your tests
    public static final String SSH_PRIVATE_KEY_FILE_WITH_TILDE = System.getProperty("sshPrivateKey", "~/.ssh/id_rsa");
    public static final String SSH_PUBLIC_KEY_FILE_WITH_TILDE = System.getProperty("sshPublicKey", "~/.ssh/id_rsa.pub");
    public static final String SSH_PRIVATE_KEY_FILE = System.getProperty("sshPrivateKeySample", "/brooklyn/location/basic/sample_id_rsa");
    public static final String SSH_PUBLIC_KEY_FILE = System.getProperty("sshPublicKeySample", "/brooklyn/location/basic/sample_id_rsa.pub");
    
    public void testPreferPrivateKeyDataOverFile() throws Exception {
        ConfigBag config = ConfigBag.newInstance();
        config.put(LocationConfigKeys.PRIVATE_KEY_DATA, "mydata");
        config.put(LocationConfigKeys.PRIVATE_KEY_FILE, SSH_PRIVATE_KEY_FILE);
        
        String data = LocationConfigUtils.getOsCredential(config).getPrivateKeyData();
        assertEquals(data, "mydata");
    }
    
    public void testPreferPubilcKeyDataOverFile() throws Exception {
        ConfigBag config = ConfigBag.newInstance();
        config.put(LocationConfigKeys.PUBLIC_KEY_DATA, "mydata");
        config.put(LocationConfigKeys.PUBLIC_KEY_FILE, SSH_PUBLIC_KEY_FILE);
        
        String data = LocationConfigUtils.getOsCredential(config).getPublicKeyData();
        assertEquals(data, "mydata");
    }
    
    @Test(groups="Integration")  // requires ~/.ssh/id_rsa
    public void testReadsPrivateKeyFileWithTildePath() throws Exception {
        ConfigBag config = ConfigBag.newInstance();
        config.put(LocationConfigKeys.PRIVATE_KEY_FILE, SSH_PRIVATE_KEY_FILE_WITH_TILDE);
        
        String data = LocationConfigUtils.getOsCredential(config).skipPassphraseValidation().get();
        assertTrue(data != null && data.length() > 0);
    }
    
    public void testReadsPrivateKeyFileWithMultipleColonSeparatedFilesWithGoodLast() throws Exception {
        ConfigBag config = ConfigBag.newInstance();
        config.put(LocationConfigKeys.PRIVATE_KEY_FILE, "/path/does/not/exist:"+SSH_PRIVATE_KEY_FILE);
        
        String data = LocationConfigUtils.getOsCredential(config).get();
        assertTrue(data != null && data.length() > 0);
    }
    
    public void testReadsPrivateKeyFileWithMultipleColonSeparatedFilesWithGoodFirst() throws Exception {
        ConfigBag config = ConfigBag.newInstance();
        config.put(LocationConfigKeys.PRIVATE_KEY_FILE, SSH_PRIVATE_KEY_FILE+":/path/does/not/exist");

        String data = LocationConfigUtils.getOsCredential(config).get();
        assertTrue(data != null && data.length() > 0);
    }
    
    @Test(groups="Integration")  // requires ~/.ssh/id_rsa
    public void testReadsPublicKeyFileWithTildePath() throws Exception {
        ConfigBag config = ConfigBag.newInstance();
        config.put(LocationConfigKeys.PUBLIC_KEY_FILE, SSH_PUBLIC_KEY_FILE_WITH_TILDE);
        
        String data = LocationConfigUtils.getOsCredential(config).skipPassphraseValidation().getPublicKeyData();
        assertTrue(data != null && data.length() > 0);
    }
    
    public void testInfersPublicKeyFileFromPrivateKeyFile() throws Exception {
        ConfigBag config = ConfigBag.newInstance();
        config.put(LocationConfigKeys.PRIVATE_KEY_FILE, SSH_PRIVATE_KEY_FILE);
        
        String data = LocationConfigUtils.getOsCredential(config).getPublicKeyData();
        assertTrue(data != null && data.length() > 0);
    }
}

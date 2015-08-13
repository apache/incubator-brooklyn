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
package org.apache.brooklyn.location.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.config.ConfigBag;

@Test
public class LocationConfigUtilsTest {

    // set these system properties differently if needed to fix your tests
    public static final String SSH_PRIVATE_KEY_FILE_WITH_TILDE = System.getProperty("sshPrivateKey", "~/.ssh/id_rsa");
    public static final String SSH_PUBLIC_KEY_FILE_WITH_TILDE = System.getProperty("sshPublicKey", "~/.ssh/id_rsa.pub");
    // these should work as they are on classpath
    public static final String SSH_PRIVATE_KEY_FILE_WITH_PASSPHRASE = System.getProperty("sshPrivateKeyWithPassphrase", "/brooklyn/util/crypto/sample_rsa_passphrase.pem");
    public static final String SSH_PRIVATE_KEY_FILE = System.getProperty("sshPrivateKeySample", "/org/apache/brooklyn/location/basic/sample_id_rsa");
    public static final String SSH_PUBLIC_KEY_FILE = System.getProperty("sshPublicKeySample", "/org/apache/brooklyn/location/basic/sample_id_rsa.pub");
    
    public void testPreferPrivateKeyDataOverFile() throws Exception {
        ConfigBag config = ConfigBag.newInstance();
        config.put(LocationConfigKeys.PRIVATE_KEY_DATA, "mydata");
        config.put(LocationConfigKeys.PRIVATE_KEY_FILE, SSH_PRIVATE_KEY_FILE);
        
        LocationConfigUtils.OsCredential creds = LocationConfigUtils.getOsCredential(config).doKeyValidation(false);
        Assert.assertTrue(creds.hasKey());
        // warnings, as it is malformed
        Assert.assertFalse(creds.getWarningMessages().isEmpty());

        String data = creds.getPrivateKeyData();
        assertEquals(data, "mydata");
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testInvalidKeyData() throws Exception {
        ConfigBag config = ConfigBag.newInstance();
        config.put(LocationConfigKeys.PRIVATE_KEY_DATA, "mydata");
        
        LocationConfigUtils.OsCredential creds = LocationConfigUtils.getOsCredential(config).doKeyValidation(false);
        Assert.assertTrue(creds.hasKey());
        Assert.assertFalse(creds.getWarningMessages().isEmpty());
        
        creds.checkNoErrors();
    }

    public void testPreferPublicKeyDataOverFileAndNoPrivateKeyRequired() throws Exception {
        ConfigBag config = ConfigBag.newInstance();
        config.put(LocationConfigKeys.PUBLIC_KEY_DATA, "mydata");
        config.put(LocationConfigKeys.PUBLIC_KEY_FILE, SSH_PUBLIC_KEY_FILE);
        config.put(LocationConfigKeys.PRIVATE_KEY_FILE, "");
        
        LocationConfigUtils.OsCredential creds = LocationConfigUtils.getOsCredential(config);
        String data = creds.getPublicKeyData();
        assertEquals(data, "mydata");
        Assert.assertNull(creds.getPreferredCredential());
        Assert.assertFalse(creds.hasPassword());
        Assert.assertFalse(creds.hasKey());
        // and not even any warnings here
        Assert.assertTrue(creds.getWarningMessages().isEmpty());
    }
    
    @Test(groups="Integration")  // requires ~/.ssh/id_rsa
    public void testReadsPrivateKeyFileWithTildePath() throws Exception {
        ConfigBag config = ConfigBag.newInstance();
        config.put(LocationConfigKeys.PRIVATE_KEY_FILE, SSH_PRIVATE_KEY_FILE_WITH_TILDE);

        // don't mind if it has a passphrase
        String data = LocationConfigUtils.getOsCredential(config).doKeyValidation(false).getPreferredCredential();
        assertTrue(data != null && data.length() > 0);
    }
    
    @Test(groups="Integration")
    public void testReadsPrivateKeyFileWithPassphrase() throws Exception {
        ConfigBag config = ConfigBag.newInstance();
        config.put(LocationConfigKeys.PRIVATE_KEY_FILE, SSH_PRIVATE_KEY_FILE_WITH_PASSPHRASE);

        LocationConfigUtils.OsCredential cred = LocationConfigUtils.getOsCredential(config).doKeyValidation(false);
        String data = cred.getPreferredCredential();
        assertTrue(data != null && data.length() > 0);
        Assert.assertFalse(data.isEmpty());
        
        cred.doKeyValidation(true);
        try {
            cred.checkNoErrors();
            Assert.fail("check should fail as passphrase needed");
        } catch (IllegalStateException exception) {
        }

        config.put(LocationConfigKeys.PRIVATE_KEY_PASSPHRASE, "passphrase");
        cred.checkNoErrors();
        
        config.put(LocationConfigKeys.PRIVATE_KEY_PASSPHRASE, "wrong_passphrase");
        try {
            cred.checkNoErrors();
            Assert.fail("check should fail as passphrase needed");
        } catch (IllegalStateException exception) {
        }
    }
    
    public void testReadsPrivateKeyFileWithMultipleColonSeparatedFilesWithGoodLast() throws Exception {
        ConfigBag config = ConfigBag.newInstance();
        config.put(LocationConfigKeys.PRIVATE_KEY_FILE, "/path/does/not/exist"+File.pathSeparator+SSH_PRIVATE_KEY_FILE);
        
        String data = LocationConfigUtils.getOsCredential(config).getPreferredCredential();
        assertTrue(data != null && data.length() > 0);
    }
    
    public void testReadsPrivateKeyFileWithMultipleColonSeparatedFilesWithGoodFirst() throws Exception {
        ConfigBag config = ConfigBag.newInstance();
        config.put(LocationConfigKeys.PRIVATE_KEY_FILE, SSH_PRIVATE_KEY_FILE+File.pathSeparator+"/path/does/not/exist");

        String data = LocationConfigUtils.getOsCredential(config).getPreferredCredential();
        assertTrue(data != null && data.length() > 0);
    }
    
    @Test(groups="Integration")  // requires ~/.ssh/id_rsa
    public void testReadsPublicKeyFileWithTildePath() throws Exception {
        ConfigBag config = ConfigBag.newInstance();
        config.put(LocationConfigKeys.PUBLIC_KEY_FILE, SSH_PUBLIC_KEY_FILE_WITH_TILDE);
        
        // don't mind if it has a passphrase
        String data = LocationConfigUtils.getOsCredential(config).doKeyValidation(false).getPublicKeyData();
        assertTrue(data != null && data.length() > 0);
    }
    
    public void testInfersPublicKeyFileFromPrivateKeyFile() throws Exception {
        ConfigBag config = ConfigBag.newInstance();
        config.put(LocationConfigKeys.PRIVATE_KEY_FILE, SSH_PRIVATE_KEY_FILE);
        
        String data = LocationConfigUtils.getOsCredential(config).getPublicKeyData();
        assertTrue(data != null && data.length() > 0);
    }
}

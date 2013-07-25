package brooklyn.location.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import brooklyn.util.config.ConfigBag;

public class LocationConfigUtilsTest {

    public static final String SSH_PRIVATE_KEY_FILE = System.getProperty("sshPrivateKey", "~/.ssh/id_rsa");
    public static final String SSH_PUBLIC_KEY_FILE = System.getProperty("sshPrivateKey", "~/.ssh/id_rsa.pub");
    
    @Test(groups="Integration")
    public void testPreferPrivateKeyDataOverFile() throws Exception {
        ConfigBag config = ConfigBag.newInstance();
        config.put(LocationConfigKeys.PRIVATE_KEY_DATA, "mydata");
        config.put(LocationConfigKeys.PRIVATE_KEY_FILE, SSH_PRIVATE_KEY_FILE);
        
        String data = LocationConfigUtils.getPrivateKeyData(config);
        assertEquals(data, "mydata");
    }
    
    @Test(groups="Integration")
    public void testPreferPubilcKeyDataOverFile() throws Exception {
        ConfigBag config = ConfigBag.newInstance();
        config.put(LocationConfigKeys.PUBLIC_KEY_DATA, "mydata");
        config.put(LocationConfigKeys.PUBLIC_KEY_FILE, SSH_PUBLIC_KEY_FILE);
        
        String data = LocationConfigUtils.getPublicKeyData(config);
        assertEquals(data, "mydata");
    }
    
    @Test(groups="Integration")
    public void testReadsPrivateKeyFileWithTildaPath() throws Exception {
        ConfigBag config = ConfigBag.newInstance();
        config.put(LocationConfigKeys.PRIVATE_KEY_FILE, SSH_PRIVATE_KEY_FILE);
        
        String data = LocationConfigUtils.getPrivateKeyData(config);
        assertTrue(data != null && data.length() > 0);
    }
    
    @Test(groups="Integration")
    public void testReadsPrivateKeyFileWithMultipleColonSeparatedFilesWithGoodLast() throws Exception {
        ConfigBag config = ConfigBag.newInstance();
        config.put(LocationConfigKeys.PRIVATE_KEY_FILE, "/path/does/not/exist:"+SSH_PRIVATE_KEY_FILE);
        
        String data = LocationConfigUtils.getPrivateKeyData(config);
        assertTrue(data != null && data.length() > 0);
    }
    
    @Test(groups="Integration")
    public void testReadsPrivateKeyFileWithMultipleColonSeparatedFilesWithGoodFirst() throws Exception {
        ConfigBag config = ConfigBag.newInstance();
        config.put(LocationConfigKeys.PRIVATE_KEY_FILE, SSH_PRIVATE_KEY_FILE+":/path/does/not/exist");
        
        String data = LocationConfigUtils.getPrivateKeyData(config);
        assertTrue(data != null && data.length() > 0);
    }
    
    @Test(groups="Integration")
    public void testReadsPublicKeyFileWithTildaPath() throws Exception {
        ConfigBag config = ConfigBag.newInstance();
        config.put(LocationConfigKeys.PUBLIC_KEY_FILE, SSH_PUBLIC_KEY_FILE);
        
        String data = LocationConfigUtils.getPublicKeyData(config);
        assertTrue(data != null && data.length() > 0);
    }
    
    @Test(groups="Integration")
    public void testInfersPublicKeyFileFromPrivateKeyFile() throws Exception {
        ConfigBag config = ConfigBag.newInstance();
        config.put(LocationConfigKeys.PRIVATE_KEY_FILE, SSH_PRIVATE_KEY_FILE);
        
        String data = LocationConfigUtils.getPublicKeyData(config);
        assertTrue(data != null && data.length() > 0);
    }
}

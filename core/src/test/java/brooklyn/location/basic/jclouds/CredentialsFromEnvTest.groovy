package brooklyn.location.basic.jclouds;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;

public class CredentialsFromEnvTest {

    protected static BrooklynProperties newSampleProps() {
        BrooklynProperties.Factory.newEmpty().addFromMap(
                ["brooklyn.jclouds.FooServers.identity":"bob",
                 "brooklyn.jclouds.FooServers.credential":"s3cr3t",
                 ]);
    }

    protected static BrooklynProperties newSamplePropsGeneric() {
        BrooklynProperties.Factory.newEmpty().addFromMap(
                ["brooklyn.jclouds.identity":"anybob",
                 "brooklyn.jclouds.credential":"s3cr3t",
                 ]);
    }
    protected static BrooklynProperties newSamplePropsCli() {
        BrooklynProperties.Factory.newEmpty().addFromMap(
                ["JCLOUDS_IDENTITY_FOOSERVERS":"clive",
                 "JCLOUDS_CREDENTIAL_FOOSERVERS":"s3cr3t",
                 ]);
    }

    @Test
    public void testUserPassword() {
        def ce = new CredentialsFromEnv(newSampleProps(), "FooServers");
        Assert.assertEquals(ce.getIdentity(), "bob")
        Assert.assertEquals(ce.getCredential(), "s3cr3t")
    }

    @Test
    public void testUserPasswordGenericSetting() {
        def ce = new CredentialsFromEnv(newSamplePropsGeneric(), "FooServers");
        Assert.assertEquals(ce.getIdentity(), "anybob")
        Assert.assertEquals(ce.getCredential(), "s3cr3t")
    }

    @Test
    public void testUserPasswordCliSetting() {
        def ce = new CredentialsFromEnv(newSamplePropsCli(), "FooServers");
        Assert.assertEquals(ce.getIdentity(), "clive")
        Assert.assertEquals(ce.getCredential(), "s3cr3t")
    }

    @Test
    public void testUserPasswordProgrammaticSetting() {
        def ce = CredentialsFromEnv.newInstance(identity:"DirectorDave", credential:"s3cr3t", "FooServers");
        Assert.assertEquals(ce.getIdentity(), "DirectorDave")
        Assert.assertEquals(ce.getCredential(), "s3cr3t")
    }


    @Test
    public void testDefaultKeyFile() {
        def ce = new CredentialsFromEnv(newSampleProps(), "FooServers");
        String keyfile = ce.getPublicKeyFile();
        Assert.assertNotNull(keyfile, "public key file not set (it should exist)");
        Assert.assertTrue(new File(keyfile).exists(), "public key file "+keyfile+" does not exist");
    }

    @Test
    public void testFailsFastWhenKeyFileNotFound() {
        boolean exception;
        try {
            def ce = new CredentialsFromEnv(newSampleProps().addFromMap(["brooklyn.jclouds.private-key-file":"/tmp/file-dont-exist"]), "FooServers");
            exception = false;
        } catch (IllegalStateException e) {
            exception = true;
        }
        if (!exception) Assert.fail("should have failed because filed doesn't exist!");
    }
}

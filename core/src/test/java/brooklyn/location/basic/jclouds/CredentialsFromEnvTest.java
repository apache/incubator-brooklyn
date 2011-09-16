package brooklyn.location.basic.jclouds;

import org.testng.annotations.Test;

public class CredentialsFromEnvTest {

    @Test
    public void testFailsFastWhenCredentialsNotFound() {
        try {
            new CredentialsFromEnv("mywrongname");
        } catch (IllegalStateException e) {
            // success
        }
    }
}

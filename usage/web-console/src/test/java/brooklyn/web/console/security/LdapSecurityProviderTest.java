package brooklyn.web.console.security;


import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.types.DirectoryEnvironmentConfig;
import org.opends.server.util.EmbeddedUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

public class LdapSecurityProviderTest {

    public static final String LDAP_REALM = "dc=example,dc=com";
    public static final String LDAP_URL = "ldap://127.0.0.1:1389";

    @BeforeClass(alwaysRun = true)
    public void beforeClass() throws Exception {
        String targetRootDir = Files.createTempDir().getAbsolutePath();
        String originalServerRootDir = getFile("ldap");
        initOpendsDirectory(originalServerRootDir, targetRootDir);

        DirectoryEnvironmentConfig config = new DirectoryEnvironmentConfig();
        config.setConfigClass(ConfigFileHandler.class);
        config.setServerRoot(new File(targetRootDir));

        EmbeddedUtils.startServer(config);
    }

    @AfterClass(alwaysRun = true)
    public void afterClass() throws Exception {
        EmbeddedUtils.stopServer("org.opends.server.tools.StopDS", org.opends.messages.Message.EMPTY);
    }

    private static void initOpendsDirectory(String originalRootDir,
                                            String targetRootDir) throws IOException {
        File workingDirectory = new File(targetRootDir);
        FileUtils.copyDirectory(new File(originalRootDir), new File(targetRootDir));

        // create missing directories
        // db backend, logs, locks
        String[] subDirectories = {"db", "ldif", "locks", "logs"};
        for (String s : subDirectories) {
            new File(workingDirectory, s).mkdir();
        }
    }

    private String getFile(String file) {
        return new File(getClass().getResource("/" + file).getFile()).getAbsolutePath();
    }

    @Test
    public void whenAuthenticated() {
        LdapSecurityProvider provider = new LdapSecurityProvider(LDAP_URL, LDAP_REALM);
        boolean success = provider.authenticate(null, "peter", "password");
        assertTrue(success);
    }

    @Test
    public void whenNotAuthenticated() {
        LdapSecurityProvider provider = new LdapSecurityProvider(LDAP_URL, LDAP_REALM);
        boolean success = provider.authenticate(null, "peter", "badpassword");
        assertFalse(success);
    }
}

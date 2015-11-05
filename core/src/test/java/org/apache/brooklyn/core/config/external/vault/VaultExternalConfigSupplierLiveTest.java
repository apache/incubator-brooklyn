package org.apache.brooklyn.core.config.external.vault;

import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.core.config.external.ExternalConfigSupplier;
import org.apache.brooklyn.util.text.Strings;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Test the operation of the Vault external configuration supplier.
 *
 * <p>To run this test, you must have a working Vault server, and set a number of properties to allow the test to
 * query your Vault server.</p>
 *
 * <p>You should start, initialise and unseal your vault according to the Vault documentation. Then you should insert
 * a secret into Vault:</p>
 *
 * <p><tt>vault write secret/test password=foobar</tt></p>
 *
 * <p>Then set system properties so that the test can reach Vault and knows about the secret:</p>
 *
 * <p><tt>-Dtest.brooklyn.vault.endpoint=http://127.0.0.1:8200<br />
 * -Dtest.brooklyn.vault.path=secret/test<br />
 * -Dtest.brooklyn.vault.propertyName=password<br />
 * -Dtest.brooklyn.vault.propertyExpectedValue=foobar</tt></p>
 *
 * <p>You will also need to configure authentication methods for the individual tests. Refer to the "see also" section
 * to find each method that needs further configuration.</p>
 *
 * @see #testAppIdAuthenticationWithAutomaticUserId()
 */
public class VaultExternalConfigSupplierLiveTest {

    private String endpoint;
    private String path;
    private String propertyName;
    private String propertyExpectedValue;

    @BeforeClass
    public void setUp() throws Exception {
        endpoint = getTestProperty("endpoint");
        path = getTestProperty("path");
        propertyName = getTestProperty("propertyName");
        propertyExpectedValue = getTestProperty("propertyExpectedValue");
    }

    private String getTestProperty(String name) {
        String propName = "test.brooklyn.vault." + name;
        String propVal = System.getProperty(propName);
        if (Strings.isBlank(propVal))
            throw new IllegalArgumentException(propName + " is not set");
        return propVal;
    }

    /**
     * Test using a hard-coded authentication token.
     *
     * <p>This provider does not do authentication, but instead uses a known token for authentication. When Vault is
     * initialised, it will give you an <em>Initial Root Token</em>, which can be used for this test. However,
     * obviously, passing around a well-known root token is A Bad Idea for use in production, and would largely undo all
     * the useful security that vault provides.</p>
     *
     * <p>Set these system properties:</p>
     *
     * <p><tt>-Dtest.brooklyn.vault.token=1091fc84-70c1-b266-b99f-781684dd0d2b</tt></p>
     */
    @Test(groups = "Live")
    public void testHardCodedToken() {
        String token = getTestProperty("token");
        ExternalConfigSupplier ecs = new VaultTokenExternalConfigSupplier(null, "test",
                ImmutableMap.of("endpoint", endpoint, "token", token, "path", path));
        assertEquals(ecs.get(propertyName), propertyExpectedValue);
    }

    /**
     * Test using the "userpass" authentication backend.
     *
     * <p>Configure Vault to enable userpass and add a new user ID with password:</p>
     *
     * <p><tt>vault auth-enable userpass<br>
     * vault write auth/userpass/users/brooklynTest password=s3kr1t policies=root  # the "root" policy allows unrestricted access, you will want to use a different policy for real use
     * </tt></p>
     *
     * <p>Set these system properties:</p>
     *
     * <p><tt>-Dtest.brooklyn.vault.username=brooklynTest<br />
     * -Dtest.brooklyn.vault.password=s3kr1t</tt></p>
     */
    @Test(groups = "Live")
    public void testUserPassAuthentication() {
        String username = getTestProperty("username");
        String password = getTestProperty("password");
        ExternalConfigSupplier ecs = new VaultUserPassExternalConfigSupplier(null, "test",
                ImmutableMap.of("endpoint", endpoint, "username", username, "password", password, "path", path));
        assertEquals(ecs.get(propertyName), propertyExpectedValue);
    }

    /**
     * Test using the "App ID" authentication backend, with a MAC-address based user ID.
     *
     * <p>First, determine the MAC address of your system. This is system dependent, but a good guess will be to
     * inspect the routing table to determine the default route, and take the MAC address of the interface that the
     * default route would use. Express the MAC address as a series of 12 low-case hexadecimal digits, without any
     * symbols.</p>
     *
     * <p>Configure Vault to enable App-ID, add a new app ID, and authorise the MAC address to the app ID:</p>
     *
     * <p><tt>/vault auth-enable app-id<br>
     * vault write auth/app-id/map/app-id/brooklyn value=root display_name=Brooklyn  # the app ID here is "brooklyn"; the "root" policy allows unrestricted access, you will want to use a different policy for real use
     * vault write auth/app-id/map/user-id/0c4de9bca2db value=brooklyn
     * </tt></p>
     *
     * <p>Set these system properties:</p>
     *
     * <p><tt>-Dtest.brooklyn.vault.appId=brooklyn</tt></p>
     */
    @Test(groups = "Live")
    public void testAppIdAuthenticationWithAutomaticUserId() {
        String appId = getTestProperty("appId");
        ExternalConfigSupplier ecs = new VaultAppIdExternalConfigSupplier(null, "test",
                ImmutableMap.of("endpoint", endpoint, "appId", appId, "path", path));
        assertEquals(ecs.get(propertyName), propertyExpectedValue);
    }

    /**
     * Test using the "App ID" authentication backend, with an explicitly-given user ID.
     *
     * <p>Configure Vault to enable App-ID, add a new app ID, and authorise your chosen user ID to the app ID:</p>
     *
     * <p><tt>/vault auth-enable app-id<br>
     * vault write auth/app-id/map/app-id/brooklyn value=root display_name=Brooklyn  # the app ID here is "brooklyn"; the "root" policy allows unrestricted access, you will want to use a different policy for real use
     * vault write auth/app-id/map/user-id/testUser value=brooklyn
     * </tt></p>
     *
     * <p>Set these system properties:</p>
     *
     * <p><tt>-Dtest.brooklyn.vault.appId=brooklyn<br />
     * -Dtest.brooklyn.vault.userId=testUser</tt></p>
     */
    @Test(groups = "Live")
    public void testAppIdAuthenticationWithExplicitUserId() {
        String appId = getTestProperty("appId");
        String userId = getTestProperty("userId");
        ExternalConfigSupplier ecs = new VaultAppIdExternalConfigSupplier(null, "test",
                ImmutableMap.of("endpoint", endpoint, "appId", appId, "path", path, "userId", userId));
        assertEquals(ecs.get(propertyName), propertyExpectedValue);
    }

}
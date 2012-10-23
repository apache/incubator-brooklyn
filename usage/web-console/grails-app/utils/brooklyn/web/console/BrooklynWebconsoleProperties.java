package brooklyn.web.console;

import brooklyn.util.internal.StringSystemProperty;

public class BrooklynWebconsoleProperties {

    public final static String BASE_NAME = "brooklyn.webconsole";

    /** e.g. brooklyn.security.provider=brooklyn.web.console.security.AnyoneSecurityProvider will allow anyone to log in;
     * default is explicitly named users, using SECURITY_PROVIDER_EXPLICIT__USERS  */
    public final static StringSystemProperty SECURITY_PROVIDER =
            new StringSystemProperty(BASE_NAME+".security.provider");
    /** explicitly set the users/passwords, e.g. in brooklyn.properties:
     * brooklyn.webconsole.security.explicit.users=admin,bob
     * brooklyn.webconsole.security.explicit.user.admin=password
     * brooklyn.webconsole.security.explicit.user.bob=bobspass
     */
    public final static StringSystemProperty SECURITY_PROVIDER_EXPLICIT__USERS =
            new StringSystemProperty(BASE_NAME+".security.explicit.users");

    public final static StringSystemProperty LDAP_URL =
                new StringSystemProperty(BASE_NAME+".security.ldap.url");

    public final static StringSystemProperty LDAP_REALM =
                  new StringSystemProperty(BASE_NAME+".security.ldap.realm");

    public static StringSystemProperty SECURITY_PROVIDER_EXPLICIT__PASSWORD(String user) {
        return new StringSystemProperty(BASE_NAME+".security.explicit.user."+user);
    }
}

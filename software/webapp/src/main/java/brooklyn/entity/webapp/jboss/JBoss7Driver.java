package brooklyn.entity.webapp.jboss;

import brooklyn.entity.webapp.JavaWebAppDriver;

public interface JBoss7Driver extends JavaWebAppDriver{

    /**
     * The path to the keystore file on the AS7 server machine.
     * Result is undefined if SSL is not enabled/configured.
     */
    public String getSslKeystoreFile();
}

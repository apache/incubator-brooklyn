/*
 * Copyright (c) 2009-2011 Cloudsoft Corporation Ltd. All rights reserved.
 * Supplied under license http://www.cloudsoftcorp.com/license/montereyDeveloperEdition
 * or such subsequent license agreed between Cloudsoft Corporation Ltd and the licensee.
 */
package brooklyn.location.basic.jclouds;


/**
 * The AWSCredenialsFromEnv
 *
 * @author aled
 **/
public class CredentialsFromEnv {

    private final String provider
    private final String identity
    private final String credential
    
    public CredentialsFromEnv(String provider) {
        this.provider = provider.toUpperCase().replace('-', '_')
        this.identity = returnValueOrThrowException("JCLOUDS_IDENTITY_"+this.provider.toUpperCase());
        this.credential = returnValueOrThrowException("JCLOUDS_CREDENTIAL_"+this.provider.toUpperCase());
    }
    
    public String getIdentity() {
        return identity
    }

    public String getCredential() {
        return credential
    }
    
    private String returnValueOrThrowException(String envProp) {
        String value =  System.getenv(envProp);
        if (value == null)
            throw new IllegalStateException("Environment variable "+envProp+" not set");
        return value;
    }               
}


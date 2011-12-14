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
    
    // FIXME use BrooklynProperties
    // and properties of the form   brooklyn.PROVIDER.id .password etc
    
    // TODO determine if this class is useful, or if we'd rather have none, 
    // or if we'd rather have a Credentials bean object and 
    // a factory method for populating from a given BrooklynProperties (and/or from the BP.Factory.newWithSystemAndEnvironment()
    // (probably best to follow jclouds lead here?)  
    
    // TODO do we want the provider-specific JcloudsLocationTests which use this?
    // normal use case i think is for live tests to run through the different entities,
    // against a single given provider (with enhancement perhaps in future for multiple providers);
    // if a provider-specific live test is just skipped (no failure) when credentials not supplied then it seems okay;
    // but not acceptable if it causes a failure when user doesn't have valid credentials for _all_ providers !  
    
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


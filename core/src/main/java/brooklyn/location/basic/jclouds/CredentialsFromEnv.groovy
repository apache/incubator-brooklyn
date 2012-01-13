/*
 * Copyright (c) 2009-2011 Cloudsoft Corporation Ltd. All rights reserved.
 * Supplied under license http://www.cloudsoftcorp.com/license/montereyDeveloperEdition
 * or such subsequent license agreed between Cloudsoft Corporation Ltd and the licensee.
 */
package brooklyn.location.basic.jclouds;

import java.lang.reflect.Field;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.config.BrooklynProperties
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.SetFromFlag


/**
 * The credentials to use for a jclouds location, loaded from environment variables / system properties
 *
 * Preferred format is:
 * 
 *   brooklyn.jclouds.aws-ec2.identity
 *   brooklyn.jclouds.aws-ec2.credentials
 *   brooklyn.jclouds.aws-ec2.public-key-file
 *   brooklyn.jclouds.aws-ec2.private-key-file
 * 
 * It will also support (in decreasing order of preference):
 * 
 *   JCLOUDS_AWS_EC2_IDENTITY  (and the others, using bash shell format)
 *   brooklyn.jclouds.identity  (and the others, just without the provider)
 *   JCLOUDS_IDENTITY  (and the others, using bash shell format without the provider)
 *   
 * @author aled, alex
 **/
public class CredentialsFromEnv {

    public static final Logger log = LoggerFactory.getLogger(CredentialsFromEnv.class);
            
    // don't need to serialize as lookup is only done initially
    private transient final BrooklynProperties sysProps

    @SetFromFlag    
    final String provider
    @SetFromFlag
    final String identity
    @SetFromFlag
    final String credential
    @SetFromFlag
    final String publicKeyFile
    @SetFromFlag
    final String privateKeyFile
    
    // TODO do we want the provider-specific JcloudsLocationTests which use this?
    // normal use case i think is for live tests to run through the different entities,
    // against a single given provider (with enhancement perhaps in future for multiple providers);
    // if a provider-specific live test is just skipped (no failure) when credentials not supplied then it seems okay;
    // but not acceptable if it causes a failure when user doesn't have valid credentials for _all_ providers !  
    
    public CredentialsFromEnv(Map properties=[:], String provider) {
        this(BrooklynProperties.Factory.newWithSystemAndEnvironment().addFromMap(properties), provider)
    }
    public CredentialsFromEnv(BrooklynProperties sysProps, String provider) {
        this.sysProps = sysProps;
        this.provider = provider;
        
        identity = getRequiredProviderSpecificValue("identity");
        credential = getRequiredProviderSpecificValue("credential")
        privateKeyFile = findProviderSpecificValueFile("private-key-file",
            ["~/.ssh/id_rsa", "~/.ssh/id_dsa"]);
        
        publicKeyFile = findProviderSpecificValueFile("public-key-file",
            privateKeyFile?[privateKeyFile+".pub"]:[])
    }

    protected String getRequiredProviderSpecificValue(String type) {
        return getProviderSpecificValue(failIfNone: true, type);
    }
    protected String getProviderSpecificValueWithDefault(String type, String defaultValue) {
        return getProviderSpecificValue(defaultIfNone: defaultValue, type);
    }
    protected String getProviderSpecificValue(Map flags=[:], String type) {
        return sysProps.getFirst(flags,
            "brooklyn.jclouds."+provider+"."+type,
            "JCLOUDS_"+convertFromPropertyToShell(type)+"_"+convertFromPropertyToShell(provider),
            "brooklyn.jclouds."+type,
            "JCLOUDS_"+convertFromPropertyToShell(type) );
    }   
    protected String findProviderSpecificValueFile(String type, List defaults) {
        String n = getProviderSpecificValue(type);
        List candidates = n ? [n] : defaults
        
        String home=null;
        for (String f: candidates) {
            if (f.startsWith("~/")) {
                if (home==null) home = sysProps.getFirst("user.home", defaultIfNone: null);
                if (home==null) home = System.getProperty("user.home")
                f = home + f.substring(1)
            }
            File ff = new File(f);
            if (ff.exists()) return f;
        }
        throw new IllegalStateException("Unable to locate "+
            (candidates.size()>1 ? "any of the candidate SSH key files "+candidates : "SSH key file "+candidates.get(0) ) );
    }
    
    static Set WARNED_MISSING_KEY_FILES = []
    
    protected static String convertFromPropertyToShell(String word) {
        word.toUpperCase().replace('-', '_');
    }
    protected static String convertFromCamelToProperty(String word) {
        String result = "";
        for (char c: word.toCharArray()) {
            if (c.isUpperCase()) {
                result += "-"
            }
            result += c.toLowerCase()
        }
        result
    }

    /** creates a new instance, allowing credentials easily to be specified, directly as keys of the form:
     * provider, identity, credential, publicKeyFile, privateKeyFile
     */
    public static CredentialsFromEnv newInstance(Map flags, String provider) {
        Map f2 = [:]
        f2.putAll(flags);
        
        //for all annotated fields, map to brooklyn.jclouds.$provider namespace
        def fields = FlagUtils.getAnnotatedFields(CredentialsFromEnv.class)
        fields.each { Field f, SetFromFlag cf ->
            String fv = f2.remove(f.getName())
            if (fv!=null) {
                f2.put("brooklyn.jclouds."+provider+"."+convertFromCamelToProperty(f.getName()), fv)
            }
        }
         
        new CredentialsFromEnv(f2, provider)        
    }
    
}


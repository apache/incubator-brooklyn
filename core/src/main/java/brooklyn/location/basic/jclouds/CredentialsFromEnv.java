package brooklyn.location.basic.jclouds;

import static brooklyn.util.GroovyJavaMethods.elvis;
import static brooklyn.util.GroovyJavaMethods.truth;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.util.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;


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
 * It will also support the following syntax (in decreasing order of preference):
 * 
 *   JCLOUDS_AWS_EC2_IDENTITY  (and the others, using bash shell format)
 *   brooklyn.jclouds.identity  (and the others, just without the provider)
 *   JCLOUDS_IDENTITY  (and the others, using bash shell format without the provider)
 *
 * A number of other properties are also supported, listed in the SUPPORTED_* maps in JcloudsLocation.
 * These include imageId, imageNameRegex, minRam, etc.  Note that the camel case referenced there
 * should be converted to the hyphenated syntax above (brooklyn.jclouds.provider.image-id)
 * or underscores in the case of environment variables (e.g. JCLOUDS_CLOUDSERVERS_UK_IMAGE_ID).
 *  
 * @author aled, alex
 **/
public class CredentialsFromEnv {

    public static final Logger log = LoggerFactory.getLogger(CredentialsFromEnv.class);
            
    // don't need to serialize as lookup is only done initially
    private transient final BrooklynProperties sysProps;

    /** map containing the extracted properties, e.g. "provider", "publicKeyFile", etc; use asMap() to access */
    protected Map<String,Object> props = Maps.newLinkedHashMap();
        
    // TODO do we want the provider-specific JcloudsLocationTests which use this?
    // normal use case i think is for live tests to run through the different entities,
    // against a single given provider (with enhancement perhaps in future for multiple providers);
    // if a provider-specific live test is just skipped (no failure) when credentials not supplied then it seems okay;
    // but not acceptable if it causes a failure when user doesn't have valid credentials for _all_ providers !  

    public CredentialsFromEnv(String provider) {
        this(MutableMap.of(), provider);
    }
    public CredentialsFromEnv(Map properties, String provider) {
        this(BrooklynProperties.Factory.newWithSystemAndEnvironment().addFromMap(properties), provider);
    }
    public CredentialsFromEnv(BrooklynProperties sysProps, String provider) {
        this.sysProps = sysProps;
        props.put("provider", provider);
        
        for (String it : JcloudsLocation.getAllSupportedProperties()) {
            String v = getProviderSpecificValue(convertFromCamelToProperty(it));
            if (v!=null) props.put(it, v);
        }
        
        //these override the above
        props.put("identity", getRequiredProviderSpecificValue("identity"));
        props.put("credential", getRequiredProviderSpecificValue("credential"));
        
        // TODO do these need to be required?:
        String privateKeyFile = elvis(findProviderSpecificValueFile("private-key-file"),
                (truth(props.get("noDefaultSshKeys")) || truth(sysProps.get("noDefaultSshKeys"))  
                        ? null : pickExistingFile(ImmutableList.of("~/.ssh/id_rsa", "~/.ssh/id_dsa"), null)));
        if (privateKeyFile != null) props.put("privateKeyFile", privateKeyFile);
        
        String publicKeyFile = elvis(findProviderSpecificValueFile("public-key-file"),
                (truth(props.get("noDefaultSshKeys")) || truth(sysProps.get("noDefaultSshKeys")) || !truth(privateKeyFile)) 
                        ? null : pickExistingFile(ImmutableList.of(privateKeyFile+".pub")));
        if (publicKeyFile != null) props.put("publicKeyFile", publicKeyFile);
        
        String privateKeyPassphrase = findProviderSpecificValueFile("passphrase");
        if (privateKeyPassphrase != null) props.put("privateKeyPassphrase", privateKeyPassphrase);
    }

    /** provider is the jclouds provider, or null if not jclouds */
    public String getProvider() { return (String) props.get("provider"); }
    /** location name is a user-suppliable name for the location, or null if no location */
    public String getLocationName() { return (String) props.get("locationName"); }
    public String getIdentity() { return (String) props.get("identity"); }
    public String getCredential() { return (String) props.get("credential"); }
    public String getPublicKeyFile() { return (String) props.get("publicKeyFile"); }
    public String getPrivateKeyFile() { return (String) props.get("privateKeyFile"); }
    
    protected String getRequiredProviderSpecificValue(String type) {
        return getProviderSpecificValue(MutableMap.of("failIfNone", true), type);
    }
    protected String getProviderSpecificValueWithDefault(String type, String defaultValue) {
        return getProviderSpecificValue(MutableMap.of("defaultIfNone", defaultValue), type);
    }
    protected String getProviderSpecificValue(String type) {
        return getProviderSpecificValue(ImmutableMap.of(), type);
    }
    protected String getProviderSpecificValue(Map flags, String type) {
        return sysProps.getFirst(flags,
            getProvider() != null ? "brooklyn.jclouds."+getProvider()+"."+type : null,
            getProvider() != null ? "JCLOUDS_"+convertFromPropertyToShell(getProvider())+"_"+convertFromPropertyToShell(type) : null,
            getProvider() != null ? "JCLOUDS_"+convertFromPropertyToShell(type)+"_"+convertFromPropertyToShell(getProvider()) : null,
            getProvider() != null ? "brooklyn.jclouds."+type : null,
            getProvider() != null ? "JCLOUDS_"+convertFromPropertyToShell(type) : null );
    }

    protected String pickExistingFile(List<String> candidates) {
        String result = pickExistingFile(candidates, null);
        if (result!=null) return result;
        throw new IllegalStateException("Unable to locate "+
                (candidates.size()!=1 ? "any of the candidate files "+candidates : "file "+candidates.get(0) ) +
                "; set brooklyn.jclouds."+getProvider()+".public-key-file" );
 
    }
    protected String pickExistingFile(List<String> candidates, String defaultIfNone) {
        if (!truth(candidates)) return null;
        
        String home=null;
        for (String f: candidates) {
            if (f==null) return f;
            if (f.startsWith("~/")) {
                if (home==null) home = sysProps.getFirst(MutableMap.of("defaultIfNone", null), "user.home");
                if (home==null) home = System.getProperty("user.home");
                f = home + f.substring(1);
            }
            File ff = new File(f);
            if (ff.exists()) return f;
        }
        return defaultIfNone;
    }
    
    protected String findProviderSpecificValueFile(String type) {
        String f = getProviderSpecificValue(type);
        if (!truth(f)) return null;
        
        String home=null;
        if (f.startsWith("~/")) {
            if (home==null) home = sysProps.getFirst(MutableMap.of("defaultIfNone", null), "user.home");
            if (home==null) home = System.getProperty("user.home");
            f = home + f.substring(1);
        }
        File ff = new File(f);
        if (ff.exists()) return f;
        throw new IllegalStateException("Unable to locate SSH key file "+f+
                "; set brooklyn.jclouds."+getProvider()+".public-key-file" );
    }
    
    static Set WARNED_MISSING_KEY_FILES = Sets.newLinkedHashSet();
    
    protected static String convertFromPropertyToShell(String word) {
        return word.toUpperCase().replace('-', '_');
    }
    protected static String convertFromCamelToProperty(String word) {
        StringBuilder result = new StringBuilder();
        for (char c: word.toCharArray()) {
            if (Character.isUpperCase(c)) {
                result.append("-");
            }
            result.append(Character.toLowerCase(c));
        }
        return result.toString();
    }

    /** creates a new instance, allowing credentials easily to be specified, directly as keys of the form:
     * provider, identity, credential, publicKeyFile, privateKeyFile
     */
    public static CredentialsFromEnv newInstance(Map<String,?> flags, String provider) {
        Map<String,Object> f2 = Maps.newLinkedHashMap();
        f2.putAll(flags);
        
        //for all annotated fields, map to brooklyn.jclouds.$provider namespace
        for (String n : JcloudsLocation.getAllSupportedProperties()) {
            Object fv = f2.remove(n);
            if (fv!=null) {
                f2.put("brooklyn.jclouds."+provider+"."+convertFromCamelToProperty(n), fv);
            }
        }
         
        return new CredentialsFromEnv(f2, provider);        
    }
    
    public Map asMap() {
        return props;
    }
    
}


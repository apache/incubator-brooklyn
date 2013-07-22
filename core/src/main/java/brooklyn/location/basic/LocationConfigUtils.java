package brooklyn.location.basic;

import static brooklyn.util.JavaGroovyEquivalents.groovyTruth;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.util.ResourceUtils;
import brooklyn.util.config.ConfigBag;

import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.io.Files;

public class LocationConfigUtils {

    private static final Logger log = LoggerFactory.getLogger(LocationConfigUtils.class);
    
    public static String getKeyData(ConfigBag config, ConfigKey<String> dataKey, ConfigKey<String> fileKey) {
        boolean unused = config.isUnused(dataKey);
        String data = config.get(dataKey);
        if (groovyTruth(data) && !unused) 
            return data;

        String file = config.get(fileKey);
        if (groovyTruth(file)) {
            String fileTidied = ResourceUtils.tidyFilePath(file);
            try {
                String fileData = Files.toString(new File(fileTidied), Charsets.UTF_8);
                if (groovyTruth(data)) {
                    if (!fileData.trim().equals(data.trim()))
                        log.warn(dataKey.getName()+" and "+fileKey.getName()+" both specified; preferring the former");
                } else {
                    data = fileData;
                    config.put(dataKey, data);
                    config.get(dataKey);
                }
            } catch (IOException e) {
                log.warn("Invalid file for "+fileKey+" (value "+file+
                        (fileTidied.equals(file) ? "" : "; converted to "+fileTidied)+
                        "); may fail provisioning "+config.getDescription());
            }
        }
        
        return data;
    }
    
    public static String getPrivateKeyData(ConfigBag config) {
        return getKeyData(config, LocationConfigKeys.PRIVATE_KEY_DATA, LocationConfigKeys.PRIVATE_KEY_FILE);
    }
    
    public static String getPublicKeyData(ConfigBag config) {
        String data = getKeyData(config, LocationConfigKeys.PUBLIC_KEY_DATA, LocationConfigKeys.PUBLIC_KEY_FILE);
        if (groovyTruth(data)) return data;
        
        String privateKeyFile = config.get(LocationConfigKeys.PRIVATE_KEY_FILE);
        if (groovyTruth(privateKeyFile)) {
            File f = new File(privateKeyFile+".pub");
            if (f.exists()) {
                log.debug("Trying to load "+LocationConfigKeys.PUBLIC_KEY_DATA.getName()+" from "+LocationConfigKeys.PRIVATE_KEY_FILE.getName() + " " + f.getAbsolutePath()+" for "+config.getDescription());
                try {
                    data = Files.toString(f, Charsets.UTF_8);
                    config.put(LocationConfigKeys.PUBLIC_KEY_DATA, data);
                    if (log.isDebugEnabled())
                        log.debug("Loaded public key "+LocationConfigKeys.PUBLIC_KEY_DATA.getName()+" from "+LocationConfigKeys.PRIVATE_KEY_FILE.getName() + " " + f.getAbsolutePath()+" for "+config.getDescription()+": "+data);
                    return data;
                } catch (IOException e) {
                    log.debug("Not able to load "+f.getAbsolutePath()+" for "+config.getDescription());
                }
            }
        }
        
        // used to also check:
        // "sshPublicKey"

        return null;
    }

    /** @deprecated since 0.6.0 use configBag.getWithDeprecation */
    @Deprecated
    @SuppressWarnings("unchecked")
    public static <T> T getConfigCheckingDeprecatedAlternatives(ConfigBag configBag, ConfigKey<T> preferredKey,
            ConfigKey<?> ...deprecatedKeys) {
        T value1 = (T) configBag.getWithDeprecation(preferredKey, deprecatedKeys);
        T value2 = getConfigCheckingDeprecatedAlternativesInternal(configBag, preferredKey, deprecatedKeys);
        if (!Objects.equal(value1, value2)) {
            // points to a bug in one of the get-with-deprecation methods
            log.warn("Deprecated getConfig with deprecated keys "+Arrays.toString(deprecatedKeys)+" gets different value with " +
            		"new strategy "+preferredKey+" ("+value1+") and old ("+value2+"); preferring old value for now, but this behaviour will change");
            return value2;
        }
        return value1;
    }
    
    private static <T> T getConfigCheckingDeprecatedAlternativesInternal(ConfigBag configBag, ConfigKey<T> preferredKey,
            ConfigKey<?> ...deprecatedKeys) {
        ConfigKey<?> keyProvidingValue = null;
        T value = null;
        boolean found = false;
        if (configBag.containsKey(preferredKey)) {
            value = configBag.get(preferredKey);
            found = true;
            keyProvidingValue = preferredKey;
        }
        
        for (ConfigKey<?> deprecatedKey: deprecatedKeys) {
            T altValue = null;
            boolean altFound = false;
            if (configBag.containsKey(deprecatedKey)) {
                altValue = (T) configBag.get(deprecatedKey);
                altFound = true;
                
                if (altFound) {
                    if (found) {
                        if (Objects.equal(value, altValue)) {
                            // fine -- nothing
                        } else {
                            log.warn("Detected deprecated key "+deprecatedKey+" with value "+altValue+" used in addition to "+keyProvidingValue+" " +
                            		"with value "+value+" for "+configBag.getDescription()+"; ignoring");
                            configBag.remove(deprecatedKey);
                        }
                    } else {
                        log.warn("Detected deprecated key "+deprecatedKey+" with value "+altValue+" used instead of recommended "+preferredKey+"; " +
                                "promoting to preferred key status; will not be supported in future versions");
                        configBag.put(preferredKey, altValue);
                        configBag.remove(deprecatedKey);
                        value = altValue;
                        found = true;
                        keyProvidingValue = deprecatedKey;
                    }
                }
            }
        }
        
        if (found) {
            return value;
        } else {
            return configBag.get(preferredKey); // get the default
        }
    }
}

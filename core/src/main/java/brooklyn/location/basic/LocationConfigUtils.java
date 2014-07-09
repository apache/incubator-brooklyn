/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.location.basic;

import static brooklyn.util.JavaGroovyEquivalents.groovyTruth;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.management.ManagementContext;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.os.Os;
import brooklyn.util.text.StringFunctions;
import brooklyn.util.text.Strings;

import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

public class LocationConfigUtils {

    private static final Logger log = LoggerFactory.getLogger(LocationConfigUtils.class);
    
    public static String getPrivateKeyData(ConfigBag config) {
        return getKeyData(config, LocationConfigKeys.PRIVATE_KEY_DATA, LocationConfigKeys.PRIVATE_KEY_FILE);
    }
    
    public static String getPublicKeyData(ConfigBag config) {
        String data = getKeyData(config, LocationConfigKeys.PUBLIC_KEY_DATA, LocationConfigKeys.PUBLIC_KEY_FILE);
        if (groovyTruth(data)) return data;
        
        String privateKeyFile = config.get(LocationConfigKeys.PRIVATE_KEY_FILE);
        if (groovyTruth(privateKeyFile)) {
            List<String> privateKeyFiles = Arrays.asList(privateKeyFile.split(File.pathSeparator));
            List<String> publicKeyFiles = ImmutableList.copyOf(Iterables.transform(privateKeyFiles, StringFunctions.append(".pub")));
            List<String> publicKeyFilesTidied = tidyFilePaths(publicKeyFiles);
            
            String fileData = getFileContents(publicKeyFilesTidied);
            if (groovyTruth(fileData)) {
                if (log.isDebugEnabled()) log.debug("Loaded "+LocationConfigKeys.PUBLIC_KEY_DATA.getName()+" from inferred files, based on "+LocationConfigKeys.PRIVATE_KEY_FILE.getName() + ": used " + publicKeyFilesTidied + " for "+config.getDescription());
                config.put(LocationConfigKeys.PUBLIC_KEY_DATA, fileData);
                return fileData;
            } else {
                log.info("Not able to load "+LocationConfigKeys.PUBLIC_KEY_DATA.getName()+" from inferred files, based on "+LocationConfigKeys.PRIVATE_KEY_FILE.getName() + ": tried " + publicKeyFilesTidied + " for "+config.getDescription());
            }
        }
        
        return null;
    }

    public static String getKeyData(ConfigBag config, ConfigKey<String> dataKey, ConfigKey<String> fileKey) {
        boolean unused = config.isUnused(dataKey);
        String data = config.get(dataKey);
        if (groovyTruth(data) && !unused) {
            return data;
        }
        
        String file = config.get(fileKey);
        if (groovyTruth(file)) {
            List<String> files = Arrays.asList(file.split(File.pathSeparator));
            List<String> filesTidied = tidyFilePaths(files);
            String fileData = getFileContents(filesTidied);
            if (fileData == null) {
                log.warn("Invalid file" + (files.size() > 1 ? "s" : "") + " for " + fileKey + " (given " + files + 
                        (files.equals(filesTidied) ? "" : "; converted to " + filesTidied) + ") " +
                        "may fail provisioning " + config.getDescription());
            } else if (groovyTruth(data)) {
                if (!fileData.trim().equals(data.trim()))
                    log.warn(dataKey.getName()+" and "+fileKey.getName()+" both specified; preferring the former");
            } else {
                data = fileData;
                config.put(dataKey, data);
                config.get(dataKey);
            }
        }
        
        return data;
    }
    
    /**
     * Reads the given file(s) in-order, returning the contents of the first file that can be read.
     * Returns the file contents, or null if none of the files can be read.
     *  
     * @param files             list of file paths
     */
    private static String getFileContents(Iterable<String> files) {
        int size = Iterables.size(files);
        int i = 0;
        
        for (String file : files) {
            if (groovyTruth(file)) {
                try {
                    File f = new File(file);
                    return Files.toString(f, Charsets.UTF_8);
                } catch (IOException e) {
                    log.debug("Invalid file "+file+" ; " + (i >= (size-1) ? "no more files to try" : "trying next file"), e);
                }
            }
            i++;
        }
        return null;
    }

    private static List<String> tidyFilePaths(Iterable<String> files) {
        List<String> result = Lists.newArrayList();
        for (String file : files) {
            result.add(Os.tidyPath(file));
        }
        return result;
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
    
    @SuppressWarnings("unchecked")
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

    public static Map<ConfigKey<String>,String> finalAndOriginalSpecs(String finalSpec, Object ...sourcesForOriginalSpec) {
        // yuck!: TODO should clean up how these things get passed around
        Map<ConfigKey<String>,String> result = MutableMap.of();
        if (finalSpec!=null) 
            result.put(LocationInternal.FINAL_SPEC, finalSpec);
        
        String originalSpec = null;
        for (Object source: sourcesForOriginalSpec) {
            if (source instanceof CharSequence) originalSpec = source.toString();
            else if (source instanceof Map) {
                if (originalSpec==null) originalSpec = Strings.toString( ((Map<?,?>)source).get(LocationInternal.ORIGINAL_SPEC) );
                if (originalSpec==null) originalSpec = Strings.toString( ((Map<?,?>)source).get(LocationInternal.ORIGINAL_SPEC.getName()) );
            }
            if (originalSpec!=null) break; 
        }
        if (originalSpec==null) originalSpec = finalSpec;
        if (originalSpec!=null)
            result.put(LocationInternal.ORIGINAL_SPEC, originalSpec);
        
        return result;
    }

    public static boolean isEnabled(ManagementContext mgmt, String prefix) {
        ConfigKey<Boolean> key = ConfigKeys.newConfigKeyWithPrefix(prefix+".", LocationConfigKeys.ENABLED);
        Boolean enabled = mgmt.getConfig().getConfig(key);
        if (enabled!=null) return enabled.booleanValue();
        return true;
    }
    

}

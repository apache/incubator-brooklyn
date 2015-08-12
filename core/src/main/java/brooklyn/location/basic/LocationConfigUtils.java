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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.management.ManagementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.internal.BrooklynFeatureEnablement;
import brooklyn.location.cloud.CloudLocationConfig;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.crypto.AuthorizedKeysParser;
import brooklyn.util.crypto.SecureKeys;
import brooklyn.util.crypto.SecureKeys.PassphraseProblem;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.os.Os;
import brooklyn.util.text.StringFunctions;
import brooklyn.util.text.Strings;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class LocationConfigUtils {

    private static final Logger log = LoggerFactory.getLogger(LocationConfigUtils.class);

    /** Creates an instance of {@link OsCredential} by inspecting {@link LocationConfigKeys#PASSWORD}; 
     * {@link LocationConfigKeys#PRIVATE_KEY_DATA} and {@link LocationConfigKeys#PRIVATE_KEY_FILE};
     * {@link LocationConfigKeys#PRIVATE_KEY_PASSPHRASE} if needed, and
     * {@link LocationConfigKeys#PRIVATE_KEY_DATA} and {@link LocationConfigKeys#PRIVATE_KEY_FILE}
     * (defaulting to the private key file + ".pub"). 
     **/
    public static OsCredential getOsCredential(ConfigBag config) {
        return OsCredential.newInstance(config);
    }
    
    /** Convenience class for holding private/public keys and passwords, inferring from config keys.
     * See {@link LocationConfigUtils#getOsCredential(ConfigBag)}. */
    @Beta // would be nice to replace with a builder pattern 
    public static class OsCredential {
        private final ConfigBag config;
        private boolean preferPassword = false;
        private boolean tryDefaultKeys = true;
        private boolean requirePublicKey = true;
        private boolean doKeyValidation = BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_VALIDATE_LOCATION_SSH_KEYS);
        private boolean warnOnErrors = true;
        private boolean throwOnErrors = false;
        
        private boolean dirty = true;;
        
        private String privateKeyData;
        private String publicKeyData;
        private String password;
        
        private OsCredential(ConfigBag config) {
            this.config = config;
        }

        /** throws if there are any problems */
        public OsCredential checkNotEmpty() {
            checkNoErrors();
            
            if (!hasKey() && !hasPassword()) {
                if (warningMessages.size()>0)
                    throw new IllegalStateException("Could not find credentials: "+warningMessages);
                else 
                    throw new IllegalStateException("Could not find credentials");
            }
            return this;
        }

        /** throws if there were errors resolving (e.g. explicit keys, none of which were found/valid, or public key required and not found) 
         * @return */
        public OsCredential checkNoErrors() {
            throwOnErrors(true);
            dirty();
            infer();
            return this;
        }
        
        public OsCredential logAnyWarnings() {
            if (!warningMessages.isEmpty())
                log.warn("When reading credentials: "+warningMessages);
            return this;
        }

        public Set<String> getWarningMessages() {
            return warningMessages;
        }
        
        /** returns either the key or password or null; if both a key and a password this prefers the key unless otherwise set
         * via {@link #preferPassword()} */
        public synchronized String getPreferredCredential() {
            infer();
            
            if (isUsingPassword()) return password;
            if (hasKey()) return privateKeyData;
            return null;
        }

        /** if there is no credential (ignores public key) */
        public boolean isEmpty() {
            return !hasKey() && !hasPassword();
        }
        public boolean hasKey() {
            infer();
            // key has stricter non-blank check than password
            return Strings.isNonBlank(privateKeyData);
        }
        public boolean hasPassword() {
            infer();
            // blank, even empty passwords are allowed
            return password!=null;
        }
        /** if a password is available, and either this is preferred over a key or there is no key */
        public boolean isUsingPassword() {
            return hasPassword() && (!hasKey() || preferPassword);
        }
        
        public String getPrivateKeyData() {
            infer();
            return privateKeyData;
        }
        public String getPublicKeyData() {
            infer();
            return publicKeyData;
        }
        public String getPassword() {
            infer();
            return password;
        }
        
        /** if both key and password supplied, prefer the key; the default */
        public OsCredential preferKey() { preferPassword = false; return dirty(); }
        /** if both key and password supplied, prefer the password; see {@link #preferKey()} */
        public OsCredential preferPassword() { preferPassword = true; return dirty(); }
        
        /** if false, do not mind if there is no public key corresponding to any private key;
         * defaults to true; only applies if a private key is set */
        public OsCredential requirePublicKey(boolean requirePublicKey) {
            this.requirePublicKey = requirePublicKey;
            return dirty(); 
        }
        /** whether to check the private/public keys and passphrase are coherent; default true */
        public OsCredential doKeyValidation(boolean doKeyValidation) {
            this.doKeyValidation = doKeyValidation;
            return dirty();
        }
        /** if true (the default) this will look at default locations set on keys */
        public OsCredential useDefaultKeys(boolean tryDefaultKeys) {
            this.tryDefaultKeys = tryDefaultKeys;
            return dirty(); 
        }
        /** whether to log warnings on problems */
        public OsCredential warnOnErrors(boolean warnOnErrors) {
            this.warnOnErrors = warnOnErrors;
            return dirty(); 
        }
        /** whether to throw on problems */
        public OsCredential throwOnErrors(boolean throwOnErrors) {
            this.throwOnErrors = throwOnErrors;
            return dirty(); 
        }
        
        private OsCredential dirty() { dirty = true; return this; }
            
        public static OsCredential newInstance(ConfigBag config) {
            return new OsCredential(config);
        }
        
        private synchronized void infer() {
            if (!dirty) return;
            warningMessages.clear(); 
            
            log.debug("Inferring OS credentials");
            privateKeyData = config.get(LocationConfigKeys.PRIVATE_KEY_DATA);
            password = config.get(LocationConfigKeys.PASSWORD);
            publicKeyData = getKeyDataFromDataKeyOrFileKey(config, LocationConfigKeys.PUBLIC_KEY_DATA, LocationConfigKeys.PUBLIC_KEY_FILE);

            KeyPair privateKey = null;
            
            if (Strings.isBlank(privateKeyData)) {
                // look up private key files
                String privateKeyFiles = null;
                boolean privateKeyFilesExplicitlySet = config.containsKey(LocationConfigKeys.PRIVATE_KEY_FILE);
                if (privateKeyFilesExplicitlySet || (tryDefaultKeys && password==null)) 
                    privateKeyFiles = config.get(LocationConfigKeys.PRIVATE_KEY_FILE);
                if (Strings.isNonBlank(privateKeyFiles)) {
                    Iterator<String> fi = Arrays.asList(privateKeyFiles.split(File.pathSeparator)).iterator();
                    while (fi.hasNext()) {
                        String file = fi.next();
                        if (Strings.isNonBlank(file)) {
                            try {
                                // real URL's won't actual work, due to use of path separator above 
                                // not real important, but we get it for free if "files" is a list instead.
                                // using ResourceUtils is useful for classpath resources
                                if (file!=null)
                                    privateKeyData = ResourceUtils.create().getResourceAsString(file);
                                // else use data already set
                                
                                privateKey = getValidatedPrivateKey(file);
                                
                                if (privateKeyData==null) {
                                    // was cleared due to validation error
                                } else if (Strings.isNonBlank(publicKeyData)) {
                                    log.debug("Loaded private key data from "+file+" (public key data explicitly set)");
                                    break;
                                } else {
                                    String publicKeyFile = (file!=null ? file+".pub" : "(data)");
                                    try {
                                        publicKeyData = ResourceUtils.create().getResourceAsString(publicKeyFile);
                                        
                                        log.debug("Loaded private key data from "+file+
                                            " and public key data from "+publicKeyFile);
                                        break;
                                    } catch (Exception e) {
                                        Exceptions.propagateIfFatal(e);
                                        log.debug("No public key file "+publicKeyFile+"; will try extracting from private key");
                                        publicKeyData = AuthorizedKeysParser.encodePublicKey(privateKey.getPublic());
                                        
                                        if (publicKeyData==null) {
                                            if (requirePublicKey) {
                                                addWarning("Unable to find or extract public key for "+file, "skipping");
                                            } else {
                                                log.debug("Loaded private key data from "+file+" (public key data not found but not required)");
                                                break;
                                            }
                                        } else {
                                            log.debug("Loaded private key data from "+file+" (public key data extracted)");
                                            break;
                                        }
                                        privateKeyData = null;
                                    }
                                }

                            } catch (Exception e) {
                                Exceptions.propagateIfFatal(e);
                                String message = "Missing/invalid private key file "+file;
                                if (privateKeyFilesExplicitlySet) addWarning(message, (!fi.hasNext() ? "no more files to try" : "trying next file")+": "+e);
                            }
                        }
                    }
                    if (privateKeyFilesExplicitlySet && Strings.isBlank(privateKeyData))
                        error("No valid private keys found", ""+warningMessages);
                }
            } else {
                privateKey = getValidatedPrivateKey("(data)");
            }
            
            if (privateKeyData!=null) {
                if (requirePublicKey && Strings.isBlank(publicKeyData)) {
                    if (privateKey!=null) {
                        publicKeyData = AuthorizedKeysParser.encodePublicKey(privateKey.getPublic());
                    }
                    if (Strings.isBlank(publicKeyData)) {
                        error("If explicit "+LocationConfigKeys.PRIVATE_KEY_DATA.getName()+" is supplied, then "
                            + "the corresponding "+LocationConfigKeys.PUBLIC_KEY_DATA.getName()+" must also be supplied.", null);
                    } else {
                        log.debug("Public key data extracted");
                    }
                }
                if (doKeyValidation && privateKey!=null && privateKey.getPublic()!=null && Strings.isNonBlank(publicKeyData)) {
                    PublicKey decoded = null;
                    try {
                        decoded = AuthorizedKeysParser.decodePublicKey(publicKeyData);
                    } catch (Exception e) {
                        Exceptions.propagateIfFatal(e);
                        addWarning("Invalid public key: "+decoded);
                    }
                    if (decoded!=null && !privateKey.getPublic().equals( decoded )) {
                        error("Public key inferred from does not match public key extracted from private key", null);
                    }
                }
            }

            log.debug("OS credential inference: "+this);
            dirty = false;
        }

        private KeyPair getValidatedPrivateKey(String label) {
            KeyPair privateKey = null;
            String passphrase = config.get(CloudLocationConfig.PRIVATE_KEY_PASSPHRASE);
            try {
                privateKey = SecureKeys.readPem(new ByteArrayInputStream(privateKeyData.getBytes()), passphrase);
                if (passphrase!=null) {
                    // get the unencrypted key data for our internal use (jclouds requires this)
                    privateKeyData = SecureKeys.toPem(privateKey);
                }
            } catch (PassphraseProblem e) {
                if (doKeyValidation) {
                    log.debug("Encountered error handling key "+label+": "+e, e);
                    if (Strings.isBlank(passphrase))
                        addWarning("Passphrase required for key '"+label+"'");
                    else
                        addWarning("Invalid passphrase for key '"+label+"'");
                    privateKeyData = null;
                }
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                if (doKeyValidation) {
                    addWarning("Unable to parse private key from '"+label+"': unknown format");
                    privateKeyData = null;
                }
            }
            return privateKey;
        }
        
        Set<String> warningMessages = MutableSet.of();
        
        private void error(String msg, String logExtension) {
            addWarning(msg);
            if (warnOnErrors) log.warn(msg+(logExtension==null ? "" : ": "+logExtension));
            if (throwOnErrors) throw new IllegalStateException(msg+(logExtension==null ? "" : "; "+logExtension));
        }

        private void addWarning(String msg) {
            addWarning(msg, null);
        }
        private void addWarning(String msg, String debugExtension) {
            log.debug(msg+(debugExtension==null ? "" : "; "+debugExtension));
            warningMessages.add(msg);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName()+"["+
                (Strings.isNonBlank(publicKeyData) ? publicKeyData : "no-public-key")+";"+
                (Strings.isNonBlank(privateKeyData) ? "private-key-present" : "no-private-key")+","+
                (password!=null ? "password(len="+password.length()+")" : "no-password")+"]";
        }
    }

    /** @deprecated since 0.7.0, use #getOsCredential(ConfigBag) */ @Deprecated
    public static String getPrivateKeyData(ConfigBag config) {
        return getKeyData(config, LocationConfigKeys.PRIVATE_KEY_DATA, LocationConfigKeys.PRIVATE_KEY_FILE);
    }
    
    /** @deprecated since 0.7.0, use #getOsCredential(ConfigBag) */ @Deprecated
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

    /** @deprecated since 0.7.0, use #getOsCredential(ConfigBag) */ @Deprecated
    public static String getKeyData(ConfigBag config, ConfigKey<String> dataKey, ConfigKey<String> fileKey) {
        return getKeyDataFromDataKeyOrFileKey(config, dataKey, fileKey);
    }
    
    private static String getKeyDataFromDataKeyOrFileKey(ConfigBag config, ConfigKey<String> dataKey, ConfigKey<String> fileKey) {
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
        Iterator<String> fi = files.iterator();
        while (fi.hasNext()) {
            String file = fi.next();
            if (groovyTruth(file)) {
                try {
                    // see comment above
                    String result = ResourceUtils.create().getResourceAsString(file);
                    if (result!=null) return result;
                    log.debug("Invalid file "+file+" ; " + (!fi.hasNext() ? "no more files to try" : "trying next file")+" (null)");
                } catch (Exception e) {
                    Exceptions.propagateIfFatal(e);
                    log.debug("Invalid file "+file+" ; " + (!fi.hasNext() ? "no more files to try" : "trying next file"), e);
                }
            }
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

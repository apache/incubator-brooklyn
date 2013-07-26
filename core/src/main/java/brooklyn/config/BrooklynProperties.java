package brooklyn.config;

import static com.google.common.base.Preconditions.checkNotNull;

import groovy.lang.Closure;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.TypeCoercions;

import com.google.common.base.Predicate;
import com.google.common.base.Throwables;

/** utils for accessing command-line and system-env properties;
 * doesn't resolve anything (unless an execution context is supplied)
 * and treats ConfigKeys as of type object when in doubt,
 * or string when that is likely wanted (e.g. {@link #getFirst(Map, String...)}
 * <p>
 * TODO methods in this class are not thread safe.
 * intention is that they are set during startup and not modified thereafter. */
@SuppressWarnings("rawtypes")
public class BrooklynProperties extends LinkedHashMap implements StringConfigMap {

    private static final long serialVersionUID = -945875483083108978L;
    protected static final Logger LOG = LoggerFactory.getLogger(BrooklynProperties.class);

    public static class Factory {
        public static BrooklynProperties newEmpty() {
            return new BrooklynProperties();
        }

        public static BrooklynProperties newDefault() {
            return new Builder().build();
        }

        public static class Builder {
            private String defaultLocationMetadataUrl = "classpath://brooklyn/location-metadata.properties";
            private String globalLocationMetadataFile = ResourceUtils.mergeFilePaths("~", ".brooklyn", "location-metadata.properties");
            private String globalPropertiesFile = ResourceUtils.mergeFilePaths("~", ".brooklyn", "brooklyn.properties");
            private String localPropertiesFile = null;
            
            /**
             * The URL of a default location-metadata.properties (for meta-data about different locations, such as iso3166 and global lat/lon). 
             * Defaults to classpath://brooklyn/location-metadata.properties
             */
            public Builder defaultLocationMetadataUrl(String val) {
                defaultLocationMetadataUrl = checkNotNull(val, "file");
                return this;
            }
            
            /**
             * The URL of a location-metadata.properties file that appends to and overwrites values in the locationMetadataUrl. 
             * Defaults to ~/.brooklyn/location-metadata.properties
             */
            public Builder globalLocationMetadataFile(String val) {
                globalLocationMetadataFile = checkNotNull(val, "file");
                return this;
            }
            
            /**
             * The URL of a shared brooklyn.properties file. Defaults to ~/.brooklyn/brooklyn.properties
             */
            public Builder globalPropertiesFile(String val) {
                globalPropertiesFile = checkNotNull(val, "file");
                return this;
            }
            
            /**
             * The URL of a brooklyn.properties file specific to this launch. Appends to and overwrites values in globalPropertiesFile.
             */
            public Builder localPropertiesFile(String val) {
                localPropertiesFile = val;
                return this;
            }
            
            public BrooklynProperties build() {
                BrooklynProperties properties = new BrooklynProperties();

                // TODO Could also read from http://brooklyn.io, for up-to-date values?
                // But might that make unit tests run very badly when developer is offline?
                addPropertiesFromUrl(properties, defaultLocationMetadataUrl);
                
                addPropertiesFromFile(properties, globalLocationMetadataFile);
                addPropertiesFromFile(properties, globalPropertiesFile);
                if (localPropertiesFile != null) addPropertiesFromFile(properties, localPropertiesFile);
                
                properties.addEnvironmentVars();
                properties.addSystemProperties();

                return properties;
            }
        }
        
        private static void addPropertiesFromUrl(BrooklynProperties p, String url) {
            try {
                p.addFrom(new ResourceUtils(BrooklynProperties.class).getResourceFromUrl(url));
            } catch (Exception e) {
                LOG.info("Could not load {}; continuing", url);
                if (LOG.isTraceEnabled()) LOG.trace("Could not load "+url+"; continuing", e);
            }
        }
        
        private static void addPropertiesFromFile(BrooklynProperties p, String file) {
            String fileTidied = ResourceUtils.tidyFilePath(file);
            File f = new File(fileTidied);

            if (f.exists()) {
                p.addFrom(f);
            }
        }
    }

    protected BrooklynProperties() {
    }

    @SuppressWarnings("unchecked")
    public BrooklynProperties addEnvironmentVars() {
        putAll(System.getenv());
        return this;
    }
    @SuppressWarnings("unchecked")
    public BrooklynProperties addSystemProperties() {
        putAll(System.getProperties());
        return this;
    }

    public BrooklynProperties addFrom(InputStream i) {
        @SuppressWarnings({ "serial" })
        Properties p = new Properties() {
            @Override
            public synchronized Object put(Object key, Object value) {
                // ugly way to load them in order
                // (Properties is a hashtable so loses order otherwise)
                return BrooklynProperties.this.put(key, value);
            }
        };
        try {
            p.load(i);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return this;
    }
    
    public BrooklynProperties addFrom(File f) {
        if (!f.exists()) {
            LOG.warn("Unable to find file '"+f.getAbsolutePath()+"' when loading properties; ignoring");
            return this;
        } else {
            try {
                return addFrom(new FileInputStream(f));
            } catch (FileNotFoundException e) {
                throw Throwables.propagate(e);
            }
        }
    }
    public BrooklynProperties addFrom(URL u) {
        try {
            return addFrom(u.openStream());
        } catch (IOException e) {
            throw new RuntimeException("Error reading properties from "+u+": "+e, e);
        }
    }
    /**
     * @see ResourceUtils#getResourceFromUrl(String)
     *
     * of the form form file:///home/... or http:// or classpath://xx ;
     * for convenience if not starting with xxx: it is treated as a classpath reference or a file;
     * throws if not found (but does nothing if argument is null)
     */
    public BrooklynProperties addFromUrl(String url) {
        try {
            if (url==null) return this;
            return addFrom(new ResourceUtils(this).getResourceFromUrl(url));
        } catch (Exception e) {
            throw new RuntimeException("Error reading properties from "+url+": "+e, e);
        }
    }

    /** expects a property already set in scope, whose value is acceptable to {@link #addFromUrl(String)};
     * if property not set, does nothing */
    public BrooklynProperties addFromUrlProperty(String urlProperty) {
        String url = (String) get(urlProperty);
        if (url==null) addFromUrl(url);
        return this;
    }

    /**
    * adds the indicated properties
    */
    @SuppressWarnings("unchecked")
    public BrooklynProperties addFromMap(Map properties) {
        putAll(properties);
        return this;
    }

    /** inserts the value under the given key, if it was not present */
    public boolean putIfAbsent(String key, Object value) {
        if (containsKey(key)) return false;
        put(key, value);
        return true;
    }

   /** @deprecated attempts to call get with this syntax are probably mistakes; get(key, defaultValue) is fine but
    * Map is unlikely the key, much more likely they meant getFirst(flags, key).
    */
   @Deprecated
   public String get(Map flags, String key) {
       LOG.warn("Discouraged use of 'BrooklynProperties.get(Map,String)' (ambiguous); use getFirst(Map,String) or get(String) -- assuming the former");
       LOG.debug("Trace for discouraged use of 'BrooklynProperties.get(Map,String)'",
           new Throwable("Arguments: "+flags+" "+key));
       return getFirst(flags, key);
   }

    /** returns the value of the first key which is defined
     * <p>
     * takes the following flags:
     * 'warnIfNone', 'failIfNone' (both taking a boolean (to use default message) or a string (which is the message));
     * and 'defaultIfNone' (a default value to return if there is no such property); defaults to no warning and null response */
    @Override
    public String getFirst(String ...keys) {
       return getFirst(MutableMap.of(), keys);
    }
    @Override
    public String getFirst(Map flags, String ...keys) {
        for (String k: keys) {
            if (k!=null && containsKey(k)) return (String) get(k);
        }
        if (flags.get("warnIfNone")!=null && !Boolean.FALSE.equals(flags.get("warnIfNone"))) {
            if (Boolean.TRUE.equals(flags.get("warnIfNone")))
                LOG.warn("Unable to find Brooklyn property "+keys);
            else
                LOG.warn(""+flags.get("warnIfNone"));
        }
        if (flags.get("failIfNone")!=null && !Boolean.FALSE.equals(flags.get("failIfNone"))) {
            Object f = flags.get("failIfNone");
            if (f instanceof Closure)
                ((Closure)f).call((Object[])keys);
            if (Boolean.TRUE.equals(f))
                throw new NoSuchElementException("Brooklyn unable to find mandatory property "+keys[0]+
                    (keys.length>1 ? " (or "+(keys.length-1)+" other possible names, full list is "+Arrays.asList(keys)+")" : "") );
            else
                throw new NoSuchElementException(""+f);
        }
        if (flags.get("defaultIfNone")!=null) {
            return (String) flags.get("defaultIfNone");
        }
        return null;
    }

    @Override
    public String toString() {
        return "BrooklynProperties["+size()+"]";
    }

    /** like normal map.put, except config keys are dereferenced on the way in */
    @SuppressWarnings("unchecked")
    public Object put(Object key, Object value) {
        if (key instanceof HasConfigKey) key = ((HasConfigKey)key).getConfigKey().getName();
        if (key instanceof ConfigKey) key = ((ConfigKey)key).getName();
        return super.put(key, value);
    }

    /** like normal map.putAll, except config keys are dereferenced on the way in */
    @Override
    public void putAll(Map vals) {
        for (Map.Entry<?,?> entry : ((Map<?,?>)vals).entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }
    
    @SuppressWarnings("unchecked")
    public <T> Object put(HasConfigKey<T> key, T value) {
        return super.put(key.getConfigKey().getName(), value);
    }

    @SuppressWarnings("unchecked")
    public <T> Object put(ConfigKey<T> key, T value) {
        return super.put(key.getName(), value);
    }
    
    @Override
    public <T> T getConfig(ConfigKey<T> key) {
        return getConfig(key, null);
    }

    @Override
    public <T> T getConfig(HasConfigKey<T> key) {
        return getConfig(key.getConfigKey(), null);
    }

    @Override
    public <T> T getConfig(HasConfigKey<T> key, T defaultValue) {
        return getConfig(key.getConfigKey(), defaultValue);
    }

    @Override
    public <T> T getConfig(ConfigKey<T> key, T defaultValue) {
        if (!containsKey(key.getName())) {
            if (defaultValue!=null) return defaultValue;
            return key.getDefaultValue();
        }
        Object value = get(key.getName());
        if (value==null) return null;
        // no evaluation / key extraction here
        return TypeCoercions.coerce(value, key.getTypeToken());
    }

    @Override
    public Object getRawConfig(ConfigKey<?> key) {
        return get(key.getName());
    }

    @Override
    public Map<ConfigKey<?>, Object> getAllConfig() {
        Map<ConfigKey<?>, Object> result = new LinkedHashMap<ConfigKey<?>, Object>();
        for (Object entry: entrySet())
            result.put(new BasicConfigKey<Object>(Object.class, ""+((Map.Entry)entry).getKey()), ((Map.Entry)entry).getValue());
        return result;
    }

    @Override
    public BrooklynProperties submap(Predicate<ConfigKey<?>> filter) {
        BrooklynProperties result = Factory.newEmpty();
        for (Object entry: entrySet()) {
            ConfigKey<?> k = new BasicConfigKey<Object>(Object.class, ""+((Map.Entry)entry).getKey());
            if (filter.apply(k))
                result.put(((Map.Entry)entry).getKey(), ((Map.Entry)entry).getValue());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> asMapWithStringKeys() {
        return this;
    }
    
}

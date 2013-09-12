package brooklyn.entity.basic;

import javax.annotation.Nonnull;

import brooklyn.config.ConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.BasicConfigKey.BasicConfigKeyOverwriting;

import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;


/**
 * Dictionary of {@link ConfigKey} entries.
 */
public class ConfigKeys {

    public static <T> ConfigKey<T> newConfigKey(Class<T> type, String name) {
        return new BasicConfigKey<T>(type, name);
    }

    public static <T> ConfigKey<T> newConfigKey(Class<T> type, String name, String description) {
        return new BasicConfigKey<T>(type, name, description);
    }

    public static <T> ConfigKey<T> newConfigKey(TypeToken<T> type, String name) {
        return new BasicConfigKey<T>(type, name);
    }

    public static <T> ConfigKey<T> newConfigKey(TypeToken<T> type, String name, String description) {
        return new BasicConfigKey<T>(type, name, description);
    }

    public static <T> ConfigKey<T> newConfigKey(Class<T> type, String name, String description, T defaultValue) {
        return new BasicConfigKey<T>(type, name, description, defaultValue);
    }

    public static <T> ConfigKey<T> newConfigKey(TypeToken<T> type, String name, String description, T defaultValue) {
        return new BasicConfigKey<T>(type, name, description, defaultValue);
    }

    /** Infers the type from the default value */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> ConfigKey<T> newConfigKey(String name, String description, @Nonnull T defaultValue) {
        return new BasicConfigKey<T>((Class)Preconditions.checkNotNull(defaultValue, 
                "Type must be exlicit for ConfigKey if defaultValue is null").getClass(), 
                name, description, defaultValue);
    }

    public static <T> BasicConfigKey.Builder<T> builder(Class<T> type) {
        return BasicConfigKey.builder(type);
    }

    public static <T> BasicConfigKey.Builder<T> builder(TypeToken<T> type) {
        return BasicConfigKey.builder(type);
    }

    // ---- extensions to keys
    
    public static <T> ConfigKey<T> newConfigKeyWithDefault(ConfigKey<T> parent, T defaultValue) {
        return new BasicConfigKeyOverwriting<T>(parent, defaultValue);
    }

    public static <T> ConfigKey<T> newConfigKeyRenamed(String newName, ConfigKey<T> key) {
        return new BasicConfigKey<T>(key.getTypeToken(), newName, key.getDescription(), key.getDefaultValue());
    }

    public static <T> ConfigKey<T> newConfigKeyWithPrefix(String prefix, ConfigKey<T> key) {
        return newConfigKeyRenamed(prefix+key.getName(), key);
    }

    /** converts the name of the key from one case-strategy (e.g. lowerCamel) to andother (e.g. lower-hyphen) */
    public static <T> ConfigKey<T> convert(ConfigKey<T> key, CaseFormat inputCaseStrategy, CaseFormat outputCaseStrategy) {
        return newConfigKeyRenamed(inputCaseStrategy.to(outputCaseStrategy, key.getName()), key);
    }

    // ---- typed keys

    public static ConfigKey<String> newStringConfigKey(String name) {
        return newConfigKey(String.class, name);
    }

    public static ConfigKey<String> newStringConfigKey(String name, String description) {
        return newConfigKey(String.class, name, description);
    }
    
    public static ConfigKey<String> newStringConfigKey(String name, String description, String defaultValue) {
        return newConfigKey(String.class, name, description, defaultValue);
    }
    
    public static ConfigKey<Integer> newIntegerConfigKey(String name) {
        return newConfigKey(Integer.class, name);
    }

    public static ConfigKey<Integer> newIntegerConfigKey(String name, String description) {
        return newConfigKey(Integer.class, name, description);
    }
    
    public static ConfigKey<Integer> newIntegerConfigKey(String name, String description, Integer defaultValue) {
        return newConfigKey(Integer.class, name, description, defaultValue);
    }
    
    public static ConfigKey<Long> newLongConfigKey(String name) {
        return newConfigKey(Long.class, name);
    }

    public static ConfigKey<Long> newLongConfigKey(String name, String description) {
        return newConfigKey(Long.class, name, description);
    }
    
    public static ConfigKey<Long> newLongConfigKey(String name, String description, Long defaultValue) {
        return newConfigKey(Long.class, name, description, defaultValue);
    }
    
    public static ConfigKey<Double> newDoubleConfigKey(String name) {
        return newConfigKey(Double.class, name);
    }

    public static ConfigKey<Double> newDoubleConfigKey(String name, String description) {
        return newConfigKey(Double.class, name, description);
    }
    
    public static ConfigKey<Double> newDoubleConfigKey(String name, String description, Double defaultValue) {
        return newConfigKey(Double.class, name, description, defaultValue);
    }
    
    public static ConfigKey<Boolean> newBooleanConfigKey(String name) {
        return newConfigKey(Boolean.class, name);
    }

    public static ConfigKey<Boolean> newBooleanConfigKey(String name, String description) {
        return newConfigKey(Boolean.class, name, description);
    }
    
    public static ConfigKey<Boolean> newBooleanConfigKey(String name, String description, Boolean defaultValue) {
        return newConfigKey(Boolean.class, name, description, defaultValue);
    }
}

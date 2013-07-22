package brooklyn.location.basic;

import java.util.Map;

import org.slf4j.Logger;

import brooklyn.config.ConfigKey;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;

/**
* @deprecated since 0.6; for use only in converting deprecated flags; will be deleted in future version.
*/
public class DeprecatedKeysMappingBuilder {
    private final ImmutableMap.Builder<String,String> builder = new ImmutableMap.Builder<String,String>();
    private final Logger logger;
    
    public DeprecatedKeysMappingBuilder(Logger logger) {
        this.logger = logger;
    }

    public DeprecatedKeysMappingBuilder camelToHyphen(ConfigKey<?> key) {
        return camelToHyphen(key.getName());
    }
    
    public DeprecatedKeysMappingBuilder camelToHyphen(String key) {
        String hyphen = toHyphen(key);
        if (key.equals(hyphen)) {
            logger.warn("Invalid attempt to convert camel-case key {} to deprecated hyphen-case: both the same", hyphen);
        } else {
            builder.put(hyphen, key);
        }
        return this;
    }
    
    public Map<String,String> build() {
        return builder.build();
    }
    
    private String toHyphen(String word) {
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, word);
    }
}

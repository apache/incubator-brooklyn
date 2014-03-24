package io.brooklyn.camp.brooklyn.spi.creation;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.proxying.EntitySpec;

import com.google.common.collect.Maps;

/**
 * Captures the {@link EntitySpec} configuration defined in YAML. 
 * 
 * This class does not parse that output; it just stores it.
 */
public class EntitySpecConfiguration {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(EntitySpecConfiguration.class);

    private final Map<String, Object> specConfiguration;

    public EntitySpecConfiguration(Map<String, ?> specConfiguration) {
        this.specConfiguration = Maps.newHashMap(checkNotNull(specConfiguration, "specConfiguration"));
    }

    public Map<String, Object> getSpecConfiguration() {
        return specConfiguration;
    }
}

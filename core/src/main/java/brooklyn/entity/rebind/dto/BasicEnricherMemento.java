package brooklyn.entity.rebind.dto;

import java.io.Serializable;
import java.util.Map;

import brooklyn.entity.basic.Entities;
import brooklyn.mementos.EnricherMemento;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.Maps;

/**
 * The persisted state of a location.
 *
 * @author aled
 */
public class BasicEnricherMemento extends AbstractMemento implements EnricherMemento, Serializable {

    private static final long serialVersionUID = 3922505388588186311L;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractMemento.Builder<Builder> {
        protected Map<String,Object> config = Maps.newLinkedHashMap();

        public Builder from(EnricherMemento other) {
            super.from(other);
            config.putAll(other.getConfig());
            return this;
        }
        public Builder config(Map<String,?> vals) {
            config.putAll(vals); return this;
        }
        public EnricherMemento build() {
            return new BasicEnricherMemento(this);
        }
    }

    private Map<String,Object> config;
    private Map<String, Object> fields;

    // Trusts the builder to not mess around with mutability after calling build()
    protected BasicEnricherMemento(Builder builder) {
        super(builder);
        config = toPersistedMap(builder.config);
    }

    @Deprecated
    @Override
    protected void setCustomFields(Map<String, Object> fields) {
        this.fields = toPersistedMap(fields);
    }

    @Deprecated
    @Override
    public Map<String, Object> getCustomFields() {
        return fromPersistedMap(fields);
    }

    @Override
    public Map<String, Object> getConfig() {
        return fromPersistedMap(config);
    }

    @Override
    protected ToStringHelper newVerboseStringHelper() {
        return super.newVerboseStringHelper().add("config", Entities.sanitize(getConfig()));
    }
}

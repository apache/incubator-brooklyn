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

    private static final long serialVersionUID = -1; // FIXME

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractMemento.Builder<Builder> {
        protected Map<String,Object> flags = Maps.newLinkedHashMap();

        public Builder from(EnricherMemento other) {
            super.from(other);
            flags.putAll(other.getFlags());
            return this;
        }
        public Builder flags(Map<String,?> vals) {
            flags.putAll(vals); return this;
        }
        public EnricherMemento build() {
            return new BasicEnricherMemento(this);
        }
    }

    private Map<String,Object> flags;
    private Map<String, Object> fields;

    // Trusts the builder to not mess around with mutability after calling build()
    protected BasicEnricherMemento(Builder builder) {
        flags = toPersistedMap(builder.flags);
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
    public Map<String, Object> getFlags() {
        return fromPersistedMap(flags);
    }

    @Override
    protected ToStringHelper newVerboseStringHelper() {
        return super.newVerboseStringHelper().add("flags", Entities.sanitize(getFlags()));
    }
}

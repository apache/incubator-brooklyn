package brooklyn.entity.rebind.dto;

import java.io.Serializable;
import java.util.Map;

import brooklyn.mementos.PolicyMemento;

import com.google.common.collect.Maps;

/**
 * The persisted state of a location.
 * 
 * @author aled
 */
public class BasicPolicyMemento extends AbstractMemento implements PolicyMemento, Serializable {

    private static final long serialVersionUID = -4025337943126838761L;
    
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractMemento.Builder<Builder> {
        protected Map<String,Object> flags = Maps.newLinkedHashMap();
        
        public Builder from(PolicyMemento other) {
            super.from(other);
            flags.putAll(other.getFlags());
            return this;
        }
        public Builder flags(Map<String,?> vals) {
            flags.putAll(vals); return this;
        }
        public PolicyMemento build() {
            return new BasicPolicyMemento(this);
        }
    }
    
	private Map<String,Object> flags;
    private Map<String, Object> fields;

    // Trusts the builder to not mess around with mutability after calling build()
	protected BasicPolicyMemento(Builder builder) {
	    flags = toPersistedMap(builder.flags);
	}
	
    protected void setCustomFields(Map<String, Object> fields) {
        this.fields = toPersistedMap(fields);
    }
    
    public Map<String, Object> getCustomFields() {
        return fromPersistedMap(fields);
    }

    @Override
    public Map<String, Object> getFlags() {
		return fromPersistedMap(flags);
	}
}

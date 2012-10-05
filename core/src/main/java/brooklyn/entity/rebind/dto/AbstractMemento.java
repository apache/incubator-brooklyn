package brooklyn.entity.rebind.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import brooklyn.mementos.Memento;
import brooklyn.mementos.TreeNode;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class AbstractMemento implements Memento, TreeNode, Serializable {

    protected static abstract class Builder<B extends Builder> {
        protected String id;
        protected String type;
        protected String parent;
        protected List<String> children = Lists.newArrayList();
        protected String displayName;
        public Map<String, Object> customProperties = Maps.newLinkedHashMap();
        
        @SuppressWarnings("unchecked")
        protected B self() {
            return (B) this;
        }
        public B from(TreeNode other) {
            id = other.getId();
            parent = other.getParent();
            children.addAll(other.getChildren());
            return self();
        }
        public B id(String val) {
            id = val; return self();
        }
        public B type(String val) {
            type = val; return self();
        }
        public B parent(String val) {
            parent = val; return self();
        }
        public B children(List<String> val) {
            children = val; return self();
        }
        public B displayName(String val) {
            displayName = val; return self();
        }
        public B addChild(String id) {
            children.add(id); return self();
        }
        public B removeChild(String id) {
            children.remove(id); return self();
        }
        public B customProperties(Map<String,?> vals) {
            customProperties.putAll(vals); return self();
        }
    }
    
    private String id;
    private String displayName;
    private String parent;
    private List<String> children;
    private Map<String,Object> customProperties;
    
    // for de-serialization
    @SuppressWarnings("unused")
    protected AbstractMemento() {
    }

    // Trusts the builder to not mess around with mutability after calling build()
    protected AbstractMemento(Builder builder) {
        id = builder.id;
        displayName = builder.displayName;
        parent = builder.parent;
        children = Collections.unmodifiableList(builder.children);
        customProperties = Collections.unmodifiableMap(builder.customProperties );
    }

    @Override
    public String getId() {
        return id;
    }
    
    @Override
    public String getParent() {
        return parent;
    }
    
    @Override
    public List<String> getChildren() {
        return children;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public Object getCustomProperty(String name) {
        return customProperties.get(name);
    }
    
    public Map<String, ? extends Object> getCustomProperties() {
        return customProperties;
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("id", id).toString();
    }
}

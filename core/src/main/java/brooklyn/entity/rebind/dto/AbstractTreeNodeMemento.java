package brooklyn.entity.rebind.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import brooklyn.mementos.Memento;
import brooklyn.mementos.TreeNode;

import com.google.common.collect.Lists;

public class AbstractTreeNodeMemento extends AbstractMemento implements Memento, TreeNode, Serializable {

    private static final long serialVersionUID = -8973379097665013550L;

    protected static abstract class Builder<B extends Builder<?>> extends AbstractMemento.Builder<B> {
        protected String parent;
        protected List<String> children = Lists.newArrayList();
        
        public B from(TreeNode other) {
            super.from((Memento)other);
            parent = other.getParent();
            children.addAll(other.getChildren());
            return self();
        }
        public B parent(String val) {
            parent = val; return self();
        }
        public B children(List<String> val) {
            children = val; return self();
        }
        public B addChild(String id) {
            children.add(id); return self();
        }
        public B removeChild(String id) {
            children.remove(id); return self();
        }
    }
    
    private String parent;
    private List<String> children;
    
    // for de-serialization
    protected AbstractTreeNodeMemento() {
    }

    // Trusts the builder to not mess around with mutability after calling build()
    protected AbstractTreeNodeMemento(Builder<?> builder) {
        super(builder);
        parent = builder.parent;
        children = Collections.unmodifiableList(builder.children);
    }

    @Override
    public String getParent() {
        return parent;
    }
    
    @Override
    public List<String> getChildren() {
        return children;
    }
}

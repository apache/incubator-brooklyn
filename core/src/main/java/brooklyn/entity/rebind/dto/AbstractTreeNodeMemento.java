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
package brooklyn.entity.rebind.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import brooklyn.mementos.Memento;
import brooklyn.mementos.TreeNode;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.Lists;

public class AbstractTreeNodeMemento extends AbstractMemento implements Memento, TreeNode, Serializable {

    private static final long serialVersionUID = -8973379097665013550L;

    protected static abstract class Builder<B extends Builder<?>> extends AbstractMemento.Builder<B> {
        boolean isValid = true;
        protected String parent;
        protected List<String> children = Lists.newArrayList();
        
        public B from(TreeNode other) {
            super.from((Memento)other);
            parent = other.getParent();
            children.addAll(other.getChildren());
            return self();
        }
        public B parent(String val) {
            assertValid();
            parent = val; return self();
        }
        public B children(List<String> val) {
            assertValid();
            children = val; return self();
        }
        public B addChild(String id) {
            assertValid();
            children.add(id); return self();
        }
        public B removeChild(String id) {
            assertValid();
            children.remove(id); return self();
        }
        /** prevent from being used twice, because the fields are mutable */
        protected void invalidate() {
            assertValid();
            isValid = false;
        }
        protected void assertValid() {
            if (!isValid) throw new IllegalStateException("Builder has already been used to build");
        }
    }
    
    private String parent;
    private List<String> children;
    private Map<String,Object> fields;
    
    // for de-serialization
    protected AbstractTreeNodeMemento() {
    }

    // Trusts the builder to not mess around with mutability after calling build()
    protected AbstractTreeNodeMemento(Builder<?> builder) {
        super(builder);
        parent = builder.parent;
        children = toPersistedList(builder.children);
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
    public String getParent() {
        return parent;
    }
    
    @Override
    public List<String> getChildren() {
        return fromPersistedList(children);
    }

    @Override
    protected ToStringHelper newVerboseStringHelper() {
        return super.newVerboseStringHelper().add("parent", getParent()).add("children", getChildren());
    }
}

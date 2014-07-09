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
package io.brooklyn.camp.spi.resolve.interpret;

import io.brooklyn.camp.spi.resolve.PlanInterpreter;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.StringPredicates;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

/** Helper class for {@link PlanInterpreter} instances, doing the recursive work */
public class PlanInterpretationNode {

    private static final Logger log = LoggerFactory.getLogger(PlanInterpretationNode.class);
    
    public enum Role { MAP_KEY, MAP_VALUE, LIST_ENTRY, YAML_PRIMITIVE }

    protected final PlanInterpretationNode parent;
    protected final Role roleInParent;
    protected final Object originalValue;
    protected final PlanInterpretationContext context;
    protected Object newValue = null;
    protected Boolean changed = null;
    protected boolean excluded = false;
    protected boolean immutable = false;

    /** creates a root node with {@link #apply()} called */
    public PlanInterpretationNode(PlanInterpretationContext context) {
        this.parent = null;
        this.roleInParent = null;
        this.originalValue = context.getOriginalDeploymentPlan();
        this.context = context;
        apply();
    }

    /** internal use: creates an internal node on which {@link #apply()} has *not* been called */
    protected PlanInterpretationNode(PlanInterpretationNode parent, Role roleInParent, Object originalItem) {
        this.parent = parent;
        this.roleInParent = roleInParent;
        this.originalValue = originalItem;
        this.context = parent.getContext();
    }

    public PlanInterpretationContext getContext() {
        return context;
    }

    public PlanInterpretationNode getParent() {
        return parent;
    }
    
    public Role getRoleInParent() {
        return roleInParent;
    }
    
    protected void apply() {
        if (changed!=null) throw new IllegalStateException("can only be applied once");

        if (!excluded) {
            if (originalValue instanceof Map) {
                applyToMap();
                immutable();
            } else if (originalValue instanceof Iterable) {
                applyToIterable();
                immutable();
            } else {
                applyToYamlPrimitive();
            }
        }
        
        if (changed==null) changed = false;
    }

    /** convenience for interpreters, tests if nodes are not excluded, and if not:
     * for string nodes, true iff the current value equals the given target;
     * for nodes which are currently maps or lists,
     * true iff not excluded and the value contains such an entry (key, in the case of map)
     **/
    public boolean matchesLiteral(String target) {
        if (isExcluded()) return false; 
        if (getNewValue() instanceof CharSequence)
            return getNewValue().toString().equals(target);
        if (getNewValue() instanceof Map)
            return ((Map<?,?>)getOriginalValue()).containsKey(target);
        if (getNewValue() instanceof Iterable)
            return Iterables.contains((Iterable<?>)getOriginalValue(), target);
        return false;
    }

    /** convenience for interpreters, tests if nodes are not excluded, and if not:
     * for string nodes, true iff the current value starts with the given prefix;
     * for nodes which are currently maps or lists,
     * true iff not excluded and the value contains such an entry (key, in the case of map) */
    public boolean matchesPrefix(String prefix) {
        if (isExcluded()) return false; 
        if (getNewValue() instanceof CharSequence)
            return getNewValue().toString().startsWith(prefix);
        if (getNewValue() instanceof Map)
            return Iterables.tryFind(((Map<?,?>)getNewValue()).keySet(), StringPredicates.isStringStartingWith(prefix)).isPresent();
        if (getNewValue() instanceof Iterable)
            return Iterables.tryFind((Iterable<?>)getNewValue(), StringPredicates.isStringStartingWith(prefix)).isPresent();
        return false;
    }
    
    // TODO matchesRegex ?

    public Object getOriginalValue() {
        return originalValue;
    }

    public Object getNewValue() {
        if (changed==null || !isChanged()) return originalValue;
        return newValue;
    }

    public boolean isChanged() {
        if (changed==null) throw new IllegalStateException("not yet applied");
        return changed;
    }

    public boolean isExcluded() {
        return excluded;
    }
    
    /** indicates that a node should no longer be translated */
    public PlanInterpretationNode exclude() {
        this.excluded = true;
        return this;
    }
    
    public PlanInterpretationNode setNewValue(Object newItem) {
        if (immutable)
            throw new IllegalStateException("Node "+this+" has been set immutable");
        this.newValue = newItem;
        this.changed = true;
        return this;
    }

    protected PlanInterpretationNode newPlanInterpretation(PlanInterpretationNode parent, Role roleInParent, Object item) {
        return new PlanInterpretationNode(parent, roleInParent, item);
    }

    protected void applyToMap() {
        Map<Object, Object> input = MutableMap.<Object,Object>copyOf((Map<?,?>)originalValue);
        Map<Object, Object> result = MutableMap.<Object,Object>of();
        newValue = result;

        // first do a "whole-node" application
        if (getContext().getAllInterpreter().applyMapBefore(this, input)) {

            for (Map.Entry<Object,Object> entry: input.entrySet()) {
                // then recurse in to this node and do various in-the-node applications
                PlanInterpretationNode value = newPlanInterpretation(this, Role.MAP_VALUE, entry.getValue());
                value.apply();

                PlanInterpretationNode key = newPlanInterpretation(this, Role.MAP_KEY, entry.getKey());
                key.apply();

                if (key.isChanged() || value.isChanged()) 
                    changed = true;

                if (getContext().getAllInterpreter().applyMapEntry(this, input, result, key, value))
                    result.put(key.getNewValue(), value.getNewValue());
                else
                    changed = true;
            }

            // finally try applying to this node again
            getContext().getAllInterpreter().applyMapAfter(this, input, result);
        }

        if (changed==null) changed = false;
    }

    protected void applyToIterable() {
        MutableList<Object> input = MutableList.copyOf((Iterable<?>)originalValue);
        MutableList<Object> result = new MutableList<Object>();
        newValue = result;

        // first do a "whole-node" application
        if (getContext().getAllInterpreter().applyListBefore(this, input)) {

            for (Object entry: input) {
                // then recurse in to this node and do various in-the-node applications
                PlanInterpretationNode value = newPlanInterpretation(this, Role.LIST_ENTRY, entry);
                value.apply();

                if (value.isChanged()) 
                    changed = true;

                if (getContext().getAllInterpreter().applyListEntry(this, input, result, value))
                    result.add(value.getNewValue());
            }

            // finally try applying to this node again
            getContext().getAllInterpreter().applyListAfter(this, input, result);
        }

        if (changed==null) changed = false;
    }

    protected void applyToYamlPrimitive() {
        getContext().getAllInterpreter().applyYamlPrimitive(this);
    }

    public void immutable() {
        if (!isChanged()) {
            if (!testCollectionImmutable(getNewValue())) {
                // results of Yaml parse are not typically immutable,
                // so force them to be changed so result of interpretation is immutable
                changed = true;
                setNewValue(immutable(getNewValue()));
            }
        } else {
            setNewValue(immutable(getNewValue()));
        }
        checkImmutable(getNewValue());
        immutable = true;
    }
    
    private void checkImmutable(Object in) {
        if (!testCollectionImmutable(in))
            log.warn("Node original value "+in+" at "+this+" should be immutable");
    }
    
    private static boolean testCollectionImmutable(Object in) {
        if (in instanceof Map) return (in instanceof ImmutableMap);
        if (in instanceof Iterable) return (in instanceof ImmutableList);
        return true;
    }

    private static Object immutable(Object in) {
        if (in instanceof Map) return ImmutableMap.copyOf((Map<?,?>)in);
        if (in instanceof Iterable) return ImmutableList.copyOf((Iterable<?>)in);
        return in;
    }

}

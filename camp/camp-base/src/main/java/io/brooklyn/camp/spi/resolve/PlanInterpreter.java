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
package io.brooklyn.camp.spi.resolve;

import io.brooklyn.camp.spi.resolve.interpret.PlanInterpretationNode;

import java.util.List;
import java.util.Map;

/** Interpreters modify the deployment plan, in a depth-first evaluation,
 * typically by looking for items which begin with "$namespace:"
 * <p>
 * Most common usages simple need to supply {@link #applyYamlPrimitive(PlanInterpretationNode)} which can invoke
 * {@link PlanInterpretationNode#setNewValue(Object)} to change.
 * The {@link PlanInterpreterAdapter} makes this easy by supplying all methods but that.
 * <p>
 * For more sophisticated usages, to act on entire maps or lists,
 * there are a number of other hook functions, described below.
 *  */
public interface PlanInterpreter {

    /** guard to prevent any apply calls when an Interpreter is not interested in a node */
    boolean isInterestedIn(PlanInterpretationNode node);
    
    /** provides an opportunity for an interpreter to change the value at a node,
     * using {@link PlanInterpretationNode#get()} and {@link PlanInterpretationNode#setNewValue(Object)} */
    void applyYamlPrimitive(PlanInterpretationNode node);

    /** invoked at a Map node in a YAML tree, before any conversion to mapOut.
     * mapIn is initially a copy of {@link PlanInterpretationNode#get()}, but it is mutable,
     * and any mutations are passed to subsequent interpreters and used for recursion.
     * <p>
     * the return value indicates whether to recurse into the item.
     * if any interpreters return false, the node is not recursed. 
     * (callers may use {@link PlanInterpretationNode#setNewValue(Object)} to set a custom return value.) */
    boolean applyMapBefore(PlanInterpretationNode node, Map<Object, Object> mapIn);

    /** invoked at a Map node in a YAML tree, after {@link #applyMapBefore(PlanInterpretationNode, Map)},
     * and after recursing into the value and then key arguments supplied here,
     * but before inserting it into the mapOut for this node. 
     * <p>
     * the return value indicates whether to add this key-value to the mapOut.
     * if any interpreters return false, the entry is not added. 
     * (callers may modify mapOut to add/change values, or may modify key/value directly.) */
    boolean applyMapEntry(PlanInterpretationNode node, Map<Object, Object> mapIn, Map<Object, Object> mapOut,
                            PlanInterpretationNode key, PlanInterpretationNode value);

    /** invoked at a Map node in a YAML tree, after all entries have been passed to all interpreters' 
     * {@link #applyMapEntry(PlanInterpretationNode, Map, Map, PlanInterpretationNode, PlanInterpretationNode)}.
     * mapOut can be modified yet further. */
    void applyMapAfter(PlanInterpretationNode node, Map<Object, Object> mapIn, Map<Object, Object> mapOut);

    /** as {@link #applyMapBefore(PlanInterpretationNode, Map)} but for lists */
    boolean applyListBefore(PlanInterpretationNode node, List<Object> listIn);

    /** as {@link #applyMapEntry(PlanInterpretationNode, Map, Map, PlanInterpretationNode, PlanInterpretationNode) but for lists */
    boolean applyListEntry(PlanInterpretationNode node, List<Object> listIn, List<Object> listOut,
                            PlanInterpretationNode value);

    /** as {@link #applyMapAfter(PlanInterpretationNode, Map, Map)} but for lists  */
    void applyListAfter(PlanInterpretationNode node, List<Object> listIn, List<Object> listOut);

    
    public abstract static class PlanInterpreterAdapter implements PlanInterpreter {

        @Override
        public boolean applyMapBefore(PlanInterpretationNode node, Map<Object, Object> mapIn) {
            return true;
        }

        @Override
        public boolean applyMapEntry(PlanInterpretationNode node, Map<Object, Object> mapIn, Map<Object, Object> mapOut,
                                PlanInterpretationNode key, PlanInterpretationNode value) {
            return true;
        }

        @Override
        public void applyMapAfter(PlanInterpretationNode node, Map<Object, Object> mapIn, Map<Object, Object> mapOut) {
        }

        @Override
        public boolean applyListBefore(PlanInterpretationNode node, List<Object> listIn) {
            return true;
        }

        @Override
        public boolean applyListEntry(PlanInterpretationNode node, List<Object> listIn, List<Object> listOut,
                                PlanInterpretationNode value) {
            return true;
        }

        @Override
        public void applyListAfter(PlanInterpretationNode node, List<Object> listIn, List<Object> listOut) {
        }
        
    }
}

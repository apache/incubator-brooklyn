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
package io.brooklyn.camp.brooklyn.spi.dsl;

import io.brooklyn.camp.brooklyn.spi.dsl.methods.BrooklynDslCommon;
import io.brooklyn.camp.brooklyn.spi.dsl.parse.DslParser;
import io.brooklyn.camp.brooklyn.spi.dsl.parse.FunctionWithArgs;
import io.brooklyn.camp.brooklyn.spi.dsl.parse.QuotedString;
import io.brooklyn.camp.spi.resolve.PlanInterpreter;
import io.brooklyn.camp.spi.resolve.PlanInterpreter.PlanInterpreterAdapter;
import io.brooklyn.camp.spi.resolve.interpret.PlanInterpretationNode;
import io.brooklyn.camp.spi.resolve.interpret.PlanInterpretationNode.Role;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.Reflections;
import brooklyn.util.text.Strings;

import com.google.common.base.Optional;

/**
 * {@link PlanInterpreter} which understands the $brooklyn DSL
 */
public class BrooklynDslInterpreter extends PlanInterpreterAdapter {

    private static final Logger log = LoggerFactory.getLogger(BrooklynDslInterpreter.class);

    @Override
    public boolean isInterestedIn(PlanInterpretationNode node) {
        return node.matchesPrefix("$brooklyn:") || node.getNewValue() instanceof FunctionWithArgs;
    }

    private static ThreadLocal<PlanInterpretationNode> currentNode = new ThreadLocal<PlanInterpretationNode>();
    /** returns the current node, stored in a thread-local, to populate the dsl field of {@link BrooklynDslDeferredSupplier} instances */
    public static PlanInterpretationNode currentNode() {
        return currentNode.get();
    }
    /** sets the current node */
    public static void currentNode(PlanInterpretationNode node) {
        currentNode.set(node);
    }
    public static void currentNodeClear() {
        currentNode.set(null);
    }
    
    @Override
    public void applyYamlPrimitive(PlanInterpretationNode node) {
        String expression = node.getNewValue().toString();

        try {
            currentNode.set(node);
            Object parsedNode = new DslParser(expression).parse();
            if ((parsedNode instanceof FunctionWithArgs) && ((FunctionWithArgs)parsedNode).getArgs()==null) {
                if (node.getRoleInParent() == Role.MAP_KEY) {
                    node.setNewValue(parsedNode);
                    // will be handled later
                } else {
                    throw new IllegalStateException("Invalid function-only expression '"+((FunctionWithArgs)parsedNode).getFunction()+"'");
                }
            } else {
                node.setNewValue( evaluate(parsedNode, true) );
            }
        } catch (Exception e) {
            log.warn("Error evaluating node (rethrowing) '"+expression+"': "+e);
            Exceptions.propagateIfFatal(e);
            throw new IllegalArgumentException("Error evaluating node '"+expression+"'", e);
        } finally {
            currentNodeClear();
        }
    }
    
    @Override
    public boolean applyMapEntry(PlanInterpretationNode node, Map<Object, Object> mapIn, Map<Object, Object> mapOut,
            PlanInterpretationNode key, PlanInterpretationNode value) {
        if (key.getNewValue() instanceof FunctionWithArgs) {
            try {
                currentNode.set(node);

                FunctionWithArgs f = (FunctionWithArgs) key.getNewValue();
                if (f.getArgs()!=null)
                    throw new IllegalStateException("Invalid map key function "+f.getFunction()+"; should not have arguments if taking arguments from map");

                // means evaluation acts on values
                List<Object> args = new ArrayList<Object>();
                if (value.getNewValue() instanceof Iterable<?>) {
                    for (Object vi: (Iterable<?>)value.getNewValue())
                        args.add(vi);
                } else {
                    args.add(value.getNewValue());
                }

                try {
                    // TODO in future we should support functions of the form 'Maps.clear', 'Maps.reset', 'Maps.remove', etc;
                    // default approach only supported if mapIn has single item and mapOut is empty
                    if (mapIn.size()!=1) throw new IllegalStateException("Map-entry DSL syntax only supported with single item in map, not "+mapIn);
                    if (mapOut.size()!=0) throw new IllegalStateException("Map-entry DSL syntax only supported with empty output map-so-far, not "+mapOut);

                    node.setNewValue( evaluate(new FunctionWithArgs(f.getFunction(), args), false) );
                    return false;
                } catch (Exception e) {
                    log.warn("Error evaluating map-entry (rethrowing) '"+f.getFunction()+args+"': "+e);
                    Exceptions.propagateIfFatal(e);
                    throw new IllegalArgumentException("Error evaluating map-entry '"+f.getFunction()+args+"'", e);
                }

            } finally {
                currentNodeClear();
            }
        }
        return super.applyMapEntry(node, mapIn, mapOut, key, value);
    }

    public Object evaluate(Object f, boolean deepEvaluation) {
        if (f instanceof FunctionWithArgs) {
            return evaluateOn(BrooklynDslCommon.class, (FunctionWithArgs) f, deepEvaluation);
        }
        
        if (f instanceof List) {
            Object o = BrooklynDslCommon.class;
            for (Object i: (List<?>)f) {
                o = evaluateOn( o, (FunctionWithArgs)i, deepEvaluation );
            }
            return o;
        }

        if (f instanceof QuotedString) {
            return ((QuotedString)f).unwrapped();
        }

        throw new IllegalArgumentException("Unexpected element in parse tree: '"+f+"' (type "+(f!=null ? f.getClass() : null)+")");
    }
    
    public Object evaluateOn(Object o, FunctionWithArgs f, boolean deepEvaluation) {
        if (f.getArgs()==null)
            throw new IllegalStateException("Invalid function-only expression '"+f.getFunction()+"'");

        Class<?> clazz;
        if (o instanceof Class) {
            clazz = (Class<?>)o;
        } else {
            clazz = o.getClass();
        }
        if (!(clazz.getPackage().getName().startsWith(BrooklynDslCommon.class.getPackage().getName())))
            throw new IllegalArgumentException("Not permitted to invoke function on '"+clazz+"' (outside allowed package scope)");
        
        String fn = f.getFunction();
        fn = Strings.removeFromStart(fn, "$brooklyn:");
        try {
            List<Object> args = new ArrayList<Object>();
            for (Object arg: f.getArgs()) {
                args.add( deepEvaluation ? evaluate(arg, true) : arg );
            }
            Optional<Object> v = Reflections.invokeMethodWithArgs(o, fn, args);
            if (v.isPresent()) return v.get();
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            throw Exceptions.propagate(new InvocationTargetException(e, "Error invoking '"+fn+"' on '"+o+"'"));
        }
        
        throw new IllegalArgumentException("No such function '"+fn+"' on "+o);
    }
    
}

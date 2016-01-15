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
package org.apache.brooklyn.core.mgmt.rebind;

import java.io.PrintStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.annotation.Nullable;

import org.apache.brooklyn.util.core.flags.FlagUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.javalang.Serializers;
import org.apache.brooklyn.util.javalang.Serializers.ObjectReplacer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Convenience for writing out an object hierarchy.
 * 
 * This is particularly useful for NotSerializableExceptions, where it does not tell you
 * which object contained the unserializable field.
 * 
 * @author aled
 */
public class Dumpers {

    private static final Logger LOG = LoggerFactory.getLogger(Dumpers.class);

    private static List<String> UNTRAVERSED_PREFIXES = ImmutableList.of("java.lang", "java.io");
    
    private static final int MAX_MEMBERS = 100;

    private static final Predicate<Field> SERIALIZED_FIELD_PREDICATE = new Predicate<Field>() {
        @Override public boolean apply(@Nullable Field input) {
            int excludedModifiers = Modifier.TRANSIENT ^ Modifier.STATIC;
            return (input.getModifiers() & excludedModifiers) == 0;
        }
    };

    public static class Pointer implements Serializable {
        private static final long serialVersionUID = 1709707205457063174L;
        private static final Random random = new Random();
        private final String id;
        private final int rand;
        
        public Pointer(String id) {
            this.id = id;
            this.rand = random.nextInt();
        }
        @Override public int hashCode() {
            return Objects.hashCode(id, rand);
        }
        @Override
        public boolean equals(Object o) {
            return (o instanceof Pointer) && Objects.equal(id, ((Pointer)o).id) && Objects.equal(rand, ((Pointer)o).rand);
        }
    }

    public static void logUnserializableChains(Object root) throws IllegalArgumentException, IllegalAccessException {
        logUnserializableChains(root, ObjectReplacer.NOOP);
    }
    
    public static void logUnserializableChains(Object root, final ObjectReplacer replacer) throws IllegalArgumentException, IllegalAccessException {
        final Map<List<Object>, Class<?>> unserializablePaths = Maps.newLinkedHashMap();
        
        Visitor visitor = new Visitor() {
            @Override public boolean visit(Object o, Iterable<Object> refChain) {
                try {
                    Serializers.reconstitute(o, replacer);
                    return true;
                } catch (Throwable e) {
                    Exceptions.propagateIfFatal(e);
                    
                    // not serializable in some way: report
                    ImmutableList<Object> refChainList = ImmutableList.copyOf(refChain);
                    // for debugging it can be useful to turn this on
//                    LOG.warn("Unreconstitutable object detected ("+o+"): "+e);
                    
                    
                    // First strip out any less specific paths
                    for (Iterator<List<Object>> iter = unserializablePaths.keySet().iterator(); iter.hasNext();) {
                        List<Object> existing = iter.next();
                        if (refChainList.size() >= existing.size() && refChainList.subList(0, existing.size()).equals(existing)) {
                            iter.remove();
                        }
                    }
                    
                    // Then add this list
                    unserializablePaths.put(ImmutableList.copyOf(refChainList), o.getClass());
                    return false;
                }
            }
        };
        deepVisitInternal(root, SERIALIZED_FIELD_PREDICATE, Lists.newArrayList(), new LinkedList<Object>(), visitor);
        
        LOG.warn("Not serializable ("+root+"):");
        for (Map.Entry<List<Object>, Class<?>> entry : unserializablePaths.entrySet()) {
            StringBuilder msg = new StringBuilder("\t"+"type="+entry.getValue()+"; chain="+"\n");
            for (Object chainElement : entry.getKey()) {
                // try-catch motivated by NPE in org.jclouds.domain.LoginCredentials.toString
                String chainElementStr;
                try {
                    chainElementStr = chainElement.toString();
                } catch (Exception e) {
                    Exceptions.propagateIfFatal(e);
                    LOG.error("Error calling toString on instance of "+chainElement.getClass(), e);
                    chainElementStr = "<error "+e.getClass().getSimpleName()+" in toString>";
                }
                msg.append("\t\t"+"type=").append(chainElement.getClass()).append("; val=").append(chainElementStr).append("\n");
            }
            LOG.warn(msg.toString());
        }
    }
    
    public static void deepDumpSerializableness(Object o) {
        deepDump(o, SERIALIZED_FIELD_PREDICATE, System.out);
    }
    
    public static void deepDump(Object o, Predicate<Field> fieldPredicate, PrintStream out) {
        try {
            out.println("Deep dump:");
            deepDumpInternal(o, fieldPredicate, out, 1, "", Lists.newArrayList());
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
    
    private static void deepDumpInternal(Object o, Predicate<Field> fieldPredicate, PrintStream out, int indentSize, String prefix, List<Object> visited) throws IllegalArgumentException, IllegalAccessException {
        String indent = com.google.common.base.Strings.repeat(" ", indentSize*2);
        Class<?> clazz = (o != null) ? o.getClass() : null;
        
        if (o == null) {
            out.println(indent+prefix+"null");
        } else if (isClassUntraversable(clazz)) {
            out.println(indent+prefix+"(untraversable) type="+clazz+"; val="+o.toString());
        } else if (containsSame(visited, o)) {
            out.println(indent+prefix+"duplicate (type="+clazz+"; val="+o.toString()+")");
        } else {
            visited.add(o);
            out.println(indent+prefix+"type="+clazz+"; val="+o.toString());
            Map<String, Object> members = findMembers(o, fieldPredicate);
            for (Map.Entry<String, Object> entry : Iterables.limit(members.entrySet(), MAX_MEMBERS)) {
                deepDumpInternal(entry.getValue(), fieldPredicate, out, indentSize+1, ""+entry.getKey()+": ", visited);
            }
            if (members.size() > MAX_MEMBERS) {
                out.println(indent+prefix+"TRUNCATED ("+members.size()+" members in total)");
            }
        }
    }
    
    private static void deepVisitInternal(Object o, Predicate<Field> fieldPredicate, List<Object> visited, Deque<Object> refChain, Visitor visitor) throws IllegalArgumentException, IllegalAccessException {
        Class<?> clazz = (o != null) ? o.getClass() : null;
        refChain.addLast(o);
        Iterable<Object> filteredRefChain = Iterables.filter(refChain, Predicates.not(Predicates.instanceOf(Dumpers.Entry.class)));
        try {
            if (o == null) {
                // no-op
            } else if (isClassUntraversable(clazz)) {
                visitor.visit(o, filteredRefChain);
            } else if (containsSame(visited, o)) {
                // no-op
            } else {
                visited.add(o);
                boolean subTreeComplete = visitor.visit(o, filteredRefChain);
                if (!subTreeComplete) {
                    Map<String, Object> members = findMembers(o, fieldPredicate);
                    for (Map.Entry<String, Object> entry : members.entrySet()) {
                        deepVisitInternal(entry.getValue(), fieldPredicate, visited, refChain, visitor);
                    }
                }
            }
        } finally {
            refChain.removeLast();
        }
    }
    
    public interface Visitor {
        /**
         * @param refChain The chain of references leading to this object (starting at the root)
         * @return True if this part of the tree is complete; false if need to continue visiting children
         */
        public boolean visit(Object o, Iterable<Object> refChain);
    }
    
    private static Map<String,Object> findMembers(Object o, Predicate<Field> fieldPredicate) throws IllegalArgumentException, IllegalAccessException {
        Map<String,Object> result = Maps.newLinkedHashMap();
        Class<?> clazz = (o != null) ? o.getClass() : null;
        
        if (o instanceof Iterable) {
            int i = 0;
            for (Object member : (Iterable<?>)o) {
                result.put("member"+(i++), member);
            }
        } else if (o instanceof Map) {
            int i = 0;
            Map<?,?> m = (Map<?,?>) o;
            for (Map.Entry<?,?> entry : m.entrySet()) {
                result.put("member"+(i++), new Entry(entry.getKey(), entry.getValue()));
            }
        } else {
            for (Field field : FlagUtils.getAllFields(clazz, fieldPredicate)) {
                field.setAccessible(true);
                String fieldName = field.getName();
                Object fieldVal = field.get(o);
                result.put(fieldName, fieldVal);
            }
        }
        
        return result;
    }
    
    private static boolean isClassUntraversable(Class<?> clazz) {
        String clazzName = clazz.getName();
        for (String prefix : UNTRAVERSED_PREFIXES) {
            if (clazzName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSame(Iterable<?> vals, Object val) {
        for (Object contender : vals) {
            if (contender == val) return true;
        }
        return false;
    }
    
    private static class Entry implements Serializable {
        private static final long serialVersionUID = -4751524179224569184L;
        
        @SuppressWarnings("unused")
        final Object key, value;
        
        public Entry(Object key, Object value) {
            this.key = key;
            this.value = value;
        }
    }
}

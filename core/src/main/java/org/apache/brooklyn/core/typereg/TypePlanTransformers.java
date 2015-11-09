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
package org.apache.brooklyn.core.typereg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.typereg.BrooklynTypeRegistry;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.api.typereg.RegisteredTypeLoadingContext;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.PropagatedRuntimeException;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

public class TypePlanTransformers {

    private static final Logger log = LoggerFactory.getLogger(TypePlanTransformers.class);

    private static Collection<BrooklynTypePlanTransformer> getAll() {
        return ImmutableList.copyOf(ServiceLoader.load(BrooklynTypePlanTransformer.class));
    }

    private static Collection<Class<? extends BrooklynTypePlanTransformer>> OVERRIDE;
    @SafeVarargs
    @VisibleForTesting
    public synchronized static void forceAvailable(Class<? extends BrooklynTypePlanTransformer> ...classes) {
        OVERRIDE = Arrays.asList(classes);
    }
    public synchronized static void clearForced() {
        OVERRIDE = null;
    }

    public static Collection<BrooklynTypePlanTransformer> all(ManagementContext mgmt) {
        // TODO cache these in the TypeRegistry, looking for new ones periodically or supplying a way to register them
        Collection<Class<? extends BrooklynTypePlanTransformer>> override = OVERRIDE;
        Collection<BrooklynTypePlanTransformer> result = new ArrayList<BrooklynTypePlanTransformer>();
        if (override!=null) {
            for (Class<? extends BrooklynTypePlanTransformer> o1: override) {
                try {
                    result.add(o1.newInstance());
                } catch (Exception e) {
                    Exceptions.propagate(e);
                }
            }
        } else {
            result.addAll(getAll());
        }
        for(BrooklynTypePlanTransformer t : result) {
            t.injectManagementContext(mgmt);
        }
        return result;
    }

    /** returns a list of {@link BrooklynTypePlanTransformer} instances for this {@link ManagementContext}
     * which may be able to handle the given plan; the list is sorted with highest-score transformer first */
    @Beta
    public static List<BrooklynTypePlanTransformer> forType(ManagementContext mgmt, RegisteredType type, RegisteredTypeLoadingContext constraint) {
        Multimap<Double,BrooklynTypePlanTransformer> byScoreMulti = ArrayListMultimap.create(); 
        Collection<BrooklynTypePlanTransformer> transformers = all(mgmt);
        for (BrooklynTypePlanTransformer transformer : transformers) {
            double score = transformer.scoreForType(type, constraint);
            if (score>0) byScoreMulti.put(score, transformer);
        }
        Map<Double, Collection<BrooklynTypePlanTransformer>> tree = new TreeMap<Double, Collection<BrooklynTypePlanTransformer>>(byScoreMulti.asMap());
        List<Collection<BrooklynTypePlanTransformer>> highestFirst = new ArrayList<Collection<BrooklynTypePlanTransformer>>(tree.values());
        Collections.reverse(highestFirst);
        return MutableList.copyOf(Iterables.concat(highestFirst)).asUnmodifiable();
    }

    /** transforms the given type to an instance, if possible
     * <p>
     * callers should generally use one of the create methods on {@link BrooklynTypeRegistry} rather than using this method directly. */
    @Beta
    public static Maybe<Object> transform(ManagementContext mgmt, RegisteredType type, RegisteredTypeLoadingContext constraint) {
        if (type==null) return Maybe.absent("type cannot be null");
        if (type.getPlan()==null) return Maybe.absent("type plan cannot be null, when instantiating "+type);
        
        List<BrooklynTypePlanTransformer> transformers = forType(mgmt, type, constraint);
        Collection<String> transformersWhoDontSupport = new ArrayList<String>();
        Collection<Exception> failuresFromTransformers = new ArrayList<Exception>();
        for (BrooklynTypePlanTransformer t: transformers) {
            try {
                Object result = t.create(type, constraint);
                if (result==null) {
                    transformersWhoDontSupport.add(t.getFormatCode() + " (returned null)");
                    continue;
                }
                return Maybe.of(result);
            } catch (UnsupportedTypePlanException e) {
                transformersWhoDontSupport.add(t.getFormatCode() +
                    (Strings.isNonBlank(e.getMessage()) ? " ("+e.getMessage()+")" : ""));
            } catch (@SuppressWarnings("deprecation") org.apache.brooklyn.core.plan.PlanNotRecognizedException e) {
                // just in case (shouldn't happen)
                transformersWhoDontSupport.add(t.getFormatCode() +
                    (Strings.isNonBlank(e.getMessage()) ? " ("+e.getMessage()+")" : ""));
            } catch (Throwable e) {
                Exceptions.propagateIfFatal(e);
                failuresFromTransformers.add(new PropagatedRuntimeException("Transformer for "+t.getFormatCode()+" gave an error creating this plan: "+
                    Exceptions.collapseText(e), e));
            }
        }
        
        // failed
        Exception result;
        if (!failuresFromTransformers.isEmpty()) {
            // at least one thought he could do it
            if (log.isDebugEnabled()) {
                log.debug("Failure transforming plan; returning summary failure, but for reference "
                    + "potentially application transformers were "+transformers+", "
                    + "others available are "+MutableList.builder().addAll(all(mgmt)).removeAll(transformers).build()+"; "
                    + "failures: "+failuresFromTransformers);
            }
            result = failuresFromTransformers.size()==1 ? Exceptions.create(null, failuresFromTransformers) :
                Exceptions.create("All plan transformers failed", failuresFromTransformers);
        } else {
            if (transformers.isEmpty()) {
                result = new UnsupportedTypePlanException("Invalid plan; format could not be recognized, none of the available transformers "+all(mgmt)+" support "+type);
            } else {
                result = new UnsupportedTypePlanException("Invalid plan; potentially applicable transformers "+transformers+" do not support it, and other available transformers "+
                    MutableList.builder().addAll(all(mgmt)).removeAll(transformers).build()+" do not accept it");
            }
        }
        return Maybe.absent(result);
    }
    
}

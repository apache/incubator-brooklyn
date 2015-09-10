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
package org.apache.brooklyn.core.plan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.ServiceLoader;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

public class PlanToSpecFactory {
    
    private static final Logger log = LoggerFactory.getLogger(PlanToSpecFactory.class);

    private static Collection<PlanToSpecTransformer> getAll() {
        return ImmutableList.copyOf(ServiceLoader.load(PlanToSpecTransformer.class));
    }

    private static Collection<Class<? extends PlanToSpecTransformer>> OVERRIDE;
    @SafeVarargs
    @VisibleForTesting
    public synchronized static void forceAvailable(Class<? extends PlanToSpecTransformer> ...classes) {
        OVERRIDE = Arrays.asList(classes);
    }
    public synchronized static void clearForced() {
        OVERRIDE = null;
    }

    public static Collection<PlanToSpecTransformer> all(ManagementContext mgmt) {
        Collection<Class<? extends PlanToSpecTransformer>> override = OVERRIDE;
        Collection<PlanToSpecTransformer> result = new ArrayList<PlanToSpecTransformer>();
        if (override!=null) {
            for (Class<? extends PlanToSpecTransformer> o1: override) {
                try {
                    result.add(o1.newInstance());
                } catch (Exception e) {
                    Exceptions.propagate(e);
                }
            }
        } else {
            result.addAll(getAll());
        }
        for(PlanToSpecTransformer t : result) {
            t.injectManagementContext(mgmt);
        }
        return result;
    }

    @Beta
    public static PlanToSpecTransformer forPlanType(ManagementContext mgmt, String planType) {
        Collection<PlanToSpecTransformer> transformers = all(mgmt);
        for (PlanToSpecTransformer transformer : transformers) {
            if (transformer.accepts(planType)) {
                return transformer;
            }
        }
        throw new IllegalStateException("PlanToSpecTransformer for plan type " + planType + " not found. Registered transformers are: " + transformers);
    }
    
    // TODO primitive loading mechanism, just tries all in order; we'll want something better as we get more plan transformers 
    @Beta
    public static <T> Maybe<T> attemptWithLoaders(ManagementContext mgmt, Function<PlanToSpecTransformer,T> f) {
        return attemptWithLoaders(all(mgmt), f);
    }
    
    public static <T> Maybe<T> attemptWithLoaders(Iterable<PlanToSpecTransformer> transformers, Function<PlanToSpecTransformer,T> f) {
        Collection<String> transformersWhoDontSupport = new ArrayList<String>();
        Collection<Exception> otherProblemsFromTransformers = new ArrayList<Exception>();
        for (PlanToSpecTransformer t: transformers) {
            try {
                T result = f.apply(t);
                if (result==null) {
                    transformersWhoDontSupport.add(t.getShortDescription() + " (returned null)");
                    continue;
                }
                return Maybe.of(result);
            } catch (PlanNotRecognizedException e) {
                transformersWhoDontSupport.add(t.getShortDescription() +
                    (Strings.isNonBlank(e.getMessage()) ? " ("+e.getMessage()+")" : ""));
            } catch (Throwable e) {
                Exceptions.propagateIfFatal(e);
                otherProblemsFromTransformers.add(new IllegalArgumentException("Transformer for "+t.getShortDescription()+" gave an error creating this plan: "+
                    Exceptions.collapseText(e), e));
            }
        }
        // failed
        Exception result;
        if (!otherProblemsFromTransformers.isEmpty()) {
            // at least one thought he could do it
            log.debug("Plan could not be transformed; failure will be propagated (other transformers tried = "+transformersWhoDontSupport+"): "+otherProblemsFromTransformers);
            result = Exceptions.create(null, otherProblemsFromTransformers);
        } else {
            result = new PlanNotRecognizedException("Invalid plan; format could not be recognized, trying with: "+transformersWhoDontSupport);
        }
        return Maybe.absent(result);
    }
    
}

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
package brooklyn.config.render;

import groovy.lang.Closure;

import java.util.Set;

import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.util.GroovyJavaMethods;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

/**
 * Registry of hints for displaying items such as sensors, e.g. in the web console.
 */
public class RendererHints {

    @VisibleForTesting
    static SetMultimap<Object, Hint<?>> registry = Multimaps.synchronizedSetMultimap(LinkedHashMultimap.<Object, Hint<?>>create());

    /**
     * Registers a {@link Hint} against the given element.
     * <p>
     * Returns the element, for convenience when used in a with block after defining the element.
     */
    public static <T> T register(T element, Hint<T> hintForThatElement) {
        registry.put(element, hintForThatElement);
        return element;
    }

    public static Set<Hint<?>> getHintsFor(Object element) {
         return getHintsFor(element, Hint.class);
    }

    public static Set<Hint<?>> getHintsFor(Object element, Class<? extends Hint> optionalHintSuperClass) {
        Set<Hint<?>> found = ImmutableSet.copyOf(registry.get(element));
        if (optionalHintSuperClass != null) {
            return Sets.filter(found, Predicates.instanceOf(optionalHintSuperClass));
        } else {
            return found;
        }
    }

    /** Parent marker class for hints. */
    public static abstract class Hint<T> { }

    public static interface NamedAction {
        String getActionName();
    }
    
    /**
     * This hint describes a named action possible on something, e.g. a sensor;
     * currently used in web client to show actions on sensors
     */
    public static class NamedActionWithUrl extends Hint<Sensor> implements NamedAction {
        private final String actionName;
        private final Function<Object, String> postProcessing;

        public NamedActionWithUrl(String actionName) {
            this(actionName, (Function<Object, String>)null);
        }

        public NamedActionWithUrl(String actionName, Closure<String> postProcessing) {
            this.actionName = actionName;
            this.postProcessing = (postProcessing == null) ? null : GroovyJavaMethods.functionFromClosure(postProcessing);
        }

        public NamedActionWithUrl(String actionName, Function<Object, String> postProcessing) {
            this.actionName = actionName;
            this.postProcessing = postProcessing;
        }

        public String getUrl(Entity e, AttributeSensor s) {
            return getUrlFromValue(e.getAttribute(s));
        }

        public String getActionName() {
            return actionName;
        }

        /** this is the method invoked by web console SensorSummary, at the moment */
        public String getUrlFromValue(Object v) {
            if (postProcessing != null) {
                v = postProcessing.apply(v);
            }
            if (v != null) {
                return "" + v;
            }
            return null;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(actionName, postProcessing);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof NamedActionWithUrl)) return false;
            NamedActionWithUrl o = (NamedActionWithUrl) obj;
            return Objects.equal(actionName, o.actionName) && Objects.equal(postProcessing, o.postProcessing);
        }
    }

    /**
     * This hint describes a transformation used to generate a display value for sensors.
     * <p>
     * <em><strong>Warning</strong> This is currently a {@link Beta} implementation, and
     * may be changed or removed if there is a suitable alternative mechanism to achieve
     * this functionality.</em>
     */
    @Beta
    public static class DisplayValue extends Hint<AttributeSensor<?>> {
        private final Function<Object, String> transform;

        public DisplayValue(Function<?, String> transform) {
            this.transform = (Function<Object, String>) Preconditions.checkNotNull(transform, "transform");
        }

        public String getDisplayValue(Entity e, AttributeSensor<?> s) {
            return getDisplayValue(e.getAttribute(s));
        }

        public String getDisplayValue(Object v) {
            String dv = transform.apply(v);
            return Strings.nullToEmpty(dv);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(transform);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof DisplayValue)) return false;
            DisplayValue o = (DisplayValue) obj;
            return Objects.equal(transform, o.transform);
        }
    }

    @Beta
    public static RendererHints.DisplayValue displayValue(Function<?, String> transform) {
        return new RendererHints.DisplayValue(transform);
    }

    @Beta
    public static RendererHints.NamedActionWithUrl namedActionWithUrl(String actionName, Function<Object, String> transform) {
        return new RendererHints.NamedActionWithUrl(actionName, transform);
    }

    @Beta
    public static RendererHints.NamedActionWithUrl openWithUrl(Function<Object, String> transform) {
        return new RendererHints.NamedActionWithUrl("Open", transform);
    }
}

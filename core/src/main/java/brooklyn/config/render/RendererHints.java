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

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.event.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.util.GroovyJavaMethods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

/**
 * Registry of hints for displaying items such as sensors, e.g. in the web console.
 */
public class RendererHints {

    private static final Logger log = LoggerFactory.getLogger(RendererHints.class);
    
    @VisibleForTesting
    static SetMultimap<Object, Hint<?>> registry = Multimaps.synchronizedSetMultimap(LinkedHashMultimap.<Object, Hint<?>>create());

    /**
     * Registers a {@link Hint} against the given element.
     * <p>
     * Returns the element, for convenience when used in a with block after defining the element.
     */
    public static <T> AttributeSensor<T> register(AttributeSensor<T> element, Hint<? super T> hintForThatElement) { return _register(element, hintForThatElement); }
    /** as {@link #register(AttributeSensor, Hint)} */
    public static <T> ConfigKey<T> register(ConfigKey<T> element, Hint<? super T> hintForThatElement) { return _register(element, hintForThatElement); }
    /** as {@link #register(AttributeSensor, Hint)} */
    public static <T> Class<T> register(Class<T> element, Hint<? super T> hintForThatElement) { return _register(element, hintForThatElement); }
    
    private static <T> T _register(T element, Hint<?> hintForThatElement) {
        if (element==null) {
            // can happen if being done in a static initializer in an inner class
            log.error("Invalid null target for renderer hint "+hintForThatElement, new Throwable("Trace for invalid null target for renderer hint"));
        }
        registry.put(element, hintForThatElement);
        return element;
    }

    /** Returns all registered hints against the given element */
    public static Set<Hint<?>> getHintsFor(AttributeSensor<?> element) { return _getHintsFor(element, null); }
    /** as {@link #getHintsFor(AttributeSensor)} */
    public static Set<Hint<?>> getHintsFor(ConfigKey<?> element) { return _getHintsFor(element, null); }
    /** as {@link #getHintsFor(AttributeSensor)} */
    public static Set<Hint<?>> getHintsFor(Class<?> element) { return _getHintsFor(element, null); }

    @Deprecated /** @deprecated since 0.7.0 only supported for certain types */
    public static Set<Hint<?>> getHintsFor(Object element) { return getHintsFor(element, null); }

    @Deprecated /** @deprecated since 0.7.0 only supported for certain types */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static Set<Hint<?>> getHintsFor(Object element, Class<? extends Hint> optionalHintSuperClass) { return (Set<Hint<?>>) _getHintsFor(element, optionalHintSuperClass); }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static <T extends Hint> Set<T> _getHintsFor(Object element, Class<T> optionalHintSuperClass) {
        Set<Hint<?>> found = ImmutableSet.copyOf(registry.get(element));
        if (found.isEmpty() && element instanceof Class && !Object.class.equals(element)) {
            // try superclasses of the element; this seems overkill for the main use case, Entity;
            // (other classes registered are typically final)
            found = (Set<Hint<?>>) _getHintsFor(((Class)element).getSuperclass(), optionalHintSuperClass);
            if (found.isEmpty()) {
                for (Class<?> parentInterface: ((Class)element).getInterfaces()) {
                    found = (Set<Hint<?>>) _getHintsFor(parentInterface, optionalHintSuperClass);
                    if (!found.isEmpty())
                        break;
                }
            }
        }
        if (optionalHintSuperClass != null) {
            return (Set<T>)Sets.filter(found, Predicates.instanceOf(optionalHintSuperClass));
        } else {
            return (Set<T>)found;
        }
    }

    /** Applies the (first) display value hint registered against the given target to the given initialValue */  
    public static Object applyDisplayValueHint(AttributeSensor<?> target, Object initialValue) { return applyDisplayValueHintUnchecked(target, initialValue); }
    /** as {@link #applyDisplayValueHint(AttributeSensor, Object)} */
    public static Object applyDisplayValueHint(ConfigKey<?> target, Object initialValue) { return applyDisplayValueHintUnchecked(target, initialValue); }
    /** as {@link #applyDisplayValueHint(AttributeSensor, Object)} */
    public static Object applyDisplayValueHint(Class<?> target, Object initialValue) { return applyDisplayValueHintUnchecked(target, initialValue); }
    
    /** as {@link #applyDisplayValueHint(AttributeSensor, Object)}, but without type checking; public for those few cases where we may have lost the type */
    @Beta
    public static Object applyDisplayValueHintUnchecked(Object target, Object initialValue) { return _applyDisplayValueHint(target, initialValue, true); }
    @SuppressWarnings("rawtypes")
    private static Object _applyDisplayValueHint(Object target, Object initialValue, boolean includeClass) {
        Iterable<RendererHints.DisplayValue> hints = RendererHints._getHintsFor(target, RendererHints.DisplayValue.class);
        if (Iterables.size(hints) > 1) {
            log.warn("Multiple display value hints set for {}; Only one will be applied, using first", target);
        }

        Optional<RendererHints.DisplayValue> hint = Optional.fromNullable(Iterables.getFirst(hints, null));
        Object value = hint.isPresent() ? hint.get().getDisplayValue(initialValue) : initialValue;
        if (includeClass && value!=null && !(value instanceof String) && !(value instanceof Number) && !(value.getClass().isPrimitive())) {
            value = _applyDisplayValueHint(value.getClass(), value, false);
        }
        return value;
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
    public static class NamedActionWithUrl<T> extends Hint<T> implements NamedAction {
        private final String actionName;
        private final Function<T, String> postProcessing;

        public NamedActionWithUrl(String actionName) {
            this(actionName, (Function<T, String>)null);
        }

        @SuppressWarnings("unchecked") @Deprecated /** @deprecated since 0.7.0 use Function */
        public NamedActionWithUrl(String actionName, Closure<String> postProcessing) {
            this.actionName = actionName;
            this.postProcessing = (Function<T, String>) ((postProcessing == null) ? null : GroovyJavaMethods.functionFromClosure(postProcessing));
        }

        public NamedActionWithUrl(String actionName, Function<T, String> postProcessing) {
            this.actionName = actionName;
            this.postProcessing = postProcessing;
        }

        /** @deprecated since 0.7.0 call {@link #getUrlFromValue(Object)}, parsing the sensor value yourself */ @Deprecated
        public String getUrl(Entity e, AttributeSensor<T> s) {
            return getUrlFromValue(e.getAttribute(s));
        }

        public String getActionName() {
            return actionName;
        }

        /** this is the method invoked by web console SensorSummary, at the moment */
        public String getUrlFromValue(T v) {
            String v2;
            if (postProcessing != null) {
                v2 = postProcessing.apply(v);
            } else {
                v2 = (v==null ? null : v.toString());
            }
            if (v2 == null) return v2;
            return v2.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(actionName, postProcessing);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof NamedActionWithUrl)) return false;
            NamedActionWithUrl<?> o = (NamedActionWithUrl<?>) obj;
            return Objects.equal(actionName, o.actionName) && Objects.equal(postProcessing, o.postProcessing);
        }
    }

    /**
     * This hint describes a transformation used to generate a display value for config keys and sensors.
     * <p>
     * <em><strong>Warning</strong> This is currently a {@link Beta} implementation, and
     * may be changed or removed if there is a suitable alternative mechanism to achieve
     * this functionality.</em>
     */
    @Beta
    public static class DisplayValue<T> extends Hint<T> {
        private final Function<Object, String> transform;

        @SuppressWarnings("unchecked")
        protected DisplayValue(Function<?, String> transform) {
            this.transform = (Function<Object, String>) Preconditions.checkNotNull(transform, "transform");
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
            if (obj == null || !(obj instanceof DisplayValue)) return false;
            return Objects.equal(transform, ((DisplayValue<?>)obj).transform);
        }
    }

    @Beta
    public static <T> DisplayValue<T> displayValue(Function<T,String> transform) {
        return new DisplayValue<T>(transform);
    }

    @Beta
    public static <T> NamedActionWithUrl<T> namedActionWithUrl(String actionName, Function<T,String> transform) {
        return new NamedActionWithUrl<T>(actionName, transform);
    }

    @Beta
    public static <T> NamedActionWithUrl<T> namedActionWithUrl(String actionName) {
        return new NamedActionWithUrl<T>(actionName);
    }

    @Beta
    public static <T> NamedActionWithUrl<T> namedActionWithUrl(Function<T,String> transform) {
        return openWithUrl(transform);
    }

    @Beta
    public static <T> NamedActionWithUrl<T> namedActionWithUrl() {
        return openWithUrl();
    }

    @Beta
    public static <T> NamedActionWithUrl<T> openWithUrl() {
        return openWithUrl((Function<T,String>) null);
    }

    @Beta
    public static <T> NamedActionWithUrl<T> openWithUrl(Function<T,String> transform) {
        return new NamedActionWithUrl<T>("Open", transform);
    }

    /**
     * Forces the given sensor or config key's value to be censored. It will be
     * presented as <code>********</code>.
     */
    @Beta
    public static <T> DisplayValue<T> censoredValue() {
        return new DisplayValue<T>(Functions.constant("********"));
    }
    
}

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
package brooklyn.event.feed;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.brooklyn.api.event.AttributeSensor;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.guava.Functionals;
import org.apache.brooklyn.util.javalang.JavaClassNames;
import org.apache.brooklyn.util.text.Strings;

import brooklyn.entity.basic.Entities;
import brooklyn.event.basic.Sensors;
import brooklyn.event.feed.http.HttpPollConfig;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;

/**
 * Configuration for a poll, or a subscription etc, that is being added to a feed.
 * 
 * @author aled
 */
public class FeedConfig<V, T, F extends FeedConfig<V, T, F>> {

    /** The onSuccess or onError functions can return this value to indicate that the sensor should not change. 
     * @deprecated since 0.7.0 use UNCHANGED */
    public static final Object UNSET = Entities.UNCHANGED;
    /** The onSuccess or onError functions can return this value to indicate that the sensor should not change. */ 
    public static final Object UNCHANGED = Entities.UNCHANGED;
    /** The onSuccess or onError functions can return this value to indicate that the sensor value should be removed
     * (cf 'null', but useful in dynamic situations) */ 
    public static final Object REMOVE = Entities.REMOVE;
    
    /** Indicates that no sensor is being used here. This sensor is suppressed,
     * but is useful where you want to use the feeds with custom success/exception/failure functions
     * which directly set multiple sensors, e.g. dynamically based on the poll response.
     * <p>
     * See {@link HttpPollConfig#forMultiple()} and its usages.
     * (It can work for any poll config, but conveniences have not been supplied for others.)  */
    public static final AttributeSensor<Void> NO_SENSOR = Sensors.newSensor(Void.class, "brooklyn.no.sensor");
    
    private final AttributeSensor<T> sensor;
    private Function<? super V, T> onsuccess;
    private Function<? super V, T> onfailure;
    private Function<? super Exception, T> onexception;
    private Predicate<? super V> checkSuccess;
    private boolean suppressDuplicates;
    private boolean enabled = true;
    
    public FeedConfig(AttributeSensor<T> sensor) {
        this.sensor = checkNotNull(sensor, "sensor");
    }

    public FeedConfig(FeedConfig<V, T, F> other) {
        this.sensor = other.sensor;
        this.onsuccess = other.onsuccess;
        this.onfailure = other.onfailure;
        this.onexception = other.onexception;
        this.checkSuccess = other.checkSuccess;
        this.suppressDuplicates = other.suppressDuplicates;
        this.enabled = other.enabled;
    }

    @SuppressWarnings("unchecked")
    protected F self() {
        return (F) this;
    }
    
    public AttributeSensor<T> getSensor() {
        return sensor;
    }

    public Predicate<? super V> getCheckSuccess() {
        return checkSuccess;
    }
    
    public Function<? super V, T> getOnSuccess() {
        return onsuccess;
    }

    public Function<? super V, T> getOnFailure() {
        return onfailure;
    }
    
    public Function<? super Exception, T> getOnException() {
        return onexception;
    }

    public boolean getSupressDuplicates() {
        return suppressDuplicates;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    /** sets the predicate used to check whether a feed run is successful */
    public F checkSuccess(Predicate<? super V> val) {
        this.checkSuccess = checkNotNull(val, "checkSuccess");
        return self();
    }
    /** as {@link #checkSuccess(Predicate)} */
    public F checkSuccess(final Function<? super V,Boolean> val) {
        return checkSuccess(Functionals.predicate(val));
    }
    @SuppressWarnings("unused")
    /** @deprecated since 0.7.0, kept for rebind */ @Deprecated
    private F checkSuccessLegacy(final Function<? super V,Boolean> val) {
        return checkSuccess(new Predicate<V>() {
            @Override
            public boolean apply(V input) {
                return val.apply(input);
            }
        });
    }

    public F onSuccess(Function<? super V,T> val) {
        this.onsuccess = checkNotNull(val, "onSuccess");
        return self();
    }
    
    public F setOnSuccess(T val) {
        return onSuccess(Functions.constant(val));
    }
    
    /** a failure is when the connection is fine (no exception) but the other end returns a result object V 
     * which the feed can tell indicates a failure (e.g. HTTP code 404) */
    public F onFailure(Function<? super V,T> val) {
        this.onfailure = checkNotNull(val, "onFailure");
        return self();
    }

    public F setOnFailure(T val) {
        return onFailure(Functions.constant(val));
    }

    /** registers a callback to be used {@link #onSuccess(Function)} and {@link #onFailure(Function)}, 
     * i.e. whenever a result comes back, but not in case of exceptions being thrown (ie problems communicating) */
    public F onResult(Function<? super V, T> val) {
        onSuccess(val);
        return onFailure(val);
    }

    public F setOnResult(T val) {
        return onResult(Functions.constant(val));
    }

    /** an exception is when there is an error in the communication */
    public F onException(Function<? super Exception,T> val) {
        this.onexception = checkNotNull(val, "onException");
        return self();
    }
    
    public F setOnException(T val) {
        return onException(Functions.constant(val));
    }

    /** convenience for indicating a behaviour to occur for both
     * {@link #onException(Function)}
     * (error connecting) and 
     * {@link #onFailure(Function)} 
     * (successful communication but failure report from remote end) */
    public F onFailureOrException(Function<Object,T> val) {
        onFailure(val);
        return onException(val);
    }
    
    public F setOnFailureOrException(T val) {
        return onFailureOrException(Functions.constant(val));
    }

    public F suppressDuplicates(boolean val) {
        suppressDuplicates = val;
        return self();
    }
    
    /**
     * Whether this feed is enabled (defaulting to true).
     */
    public F enabled(boolean val) {
        enabled = val;
        return self();
    }
    
    public boolean hasSuccessHandler() {
        return this.onsuccess != null;
    }

    public boolean hasFailureHandler() {
        return this.onfailure != null;
    }

    public boolean hasExceptionHandler() {
        return this.onexception != null;
    }

    public boolean hasCheckSuccessHandler() {
        return this.checkSuccess != null;
    }

    
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(toStringBaseName());
        result.append("[");
        boolean contents = false;
        Object source = toStringPollSource();
        AttributeSensor<T> s = getSensor();
        if (Strings.isNonBlank(Strings.toString(source))) {
            result.append(Strings.toUniqueString(source, 40));
            if (s!=null) {
                result.append("->");
                result.append(s.getName());
            }
            contents = true;
        } else if (s!=null) {
            result.append(s.getName());
            contents = true;
        }
        MutableList<Object> fields = toStringOtherFields();
        if (fields!=null) {
            for (Object field: fields) {
                if (Strings.isNonBlank(Strings.toString(field))) {
                    if (contents) result.append(";");
                    contents = true;
                    result.append(field);
                }
            }
        }
        result.append("]");
        return result.toString();
    }

    /** can be overridden to supply a simpler base name than the class name */
    protected String toStringBaseName() {
        return JavaClassNames.simpleClassName(this);
    }
    /** can be overridden to supply add'l info for the {@link #toString()}; subclasses can add to the returned value */
    protected MutableList<Object> toStringOtherFields() {
        return MutableList.<Object>of();
    }
    /** can be overridden to supply add'l info for the {@link #toString()}, placed before the sensor with -> */
    protected Object toStringPollSource() {
        return null;
    }
    /** all configs should supply a unique tag element, inserted into the feed */
    protected String getUniqueTag() {
        return toString();
    }

    /** returns fields which should be used for equality, including by default {@link #toStringOtherFields()} and {@link #toStringPollSource()};
     * subclasses can add to the returned value */
    protected MutableList<Object> equalsFields() {
        MutableList<Object> result = MutableList.of().appendIfNotNull(getSensor()).appendIfNotNull(toStringPollSource());
        for (Object field: toStringOtherFields()) result.appendIfNotNull(field);
        return result;
    }

    @Override
    public int hashCode() { 
        int hc = super.hashCode();
        for (Object f: equalsFields())
            hc = Objects.hashCode(hc, f);
        return hc;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        PollConfig<?,?,?> other = (PollConfig<?,?,?>) obj;
        if (!Objects.equal(getUniqueTag(), other.getUniqueTag())) return false;
        if (!Objects.equal(equalsFields(), other.equalsFields())) return false;
        return true;
    }

}

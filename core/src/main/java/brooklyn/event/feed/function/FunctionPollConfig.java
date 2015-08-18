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
package brooklyn.event.feed.function;

import static com.google.common.base.Preconditions.checkNotNull;
import groovy.lang.Closure;

import java.util.concurrent.Callable;

import org.apache.brooklyn.api.event.AttributeSensor;
import org.apache.brooklyn.util.guava.Functionals;
import org.apache.brooklyn.util.javalang.JavaClassNames;

import brooklyn.event.feed.FeedConfig;
import brooklyn.event.feed.PollConfig;
import brooklyn.util.GroovyJavaMethods;

import com.google.common.base.Supplier;

public class FunctionPollConfig<S, T> extends PollConfig<S, T, FunctionPollConfig<S, T>> {

    private Callable<?> callable;
    
    public static <T> FunctionPollConfig<?, T> forSensor(AttributeSensor<T> sensor) {
        return new FunctionPollConfig<Object, T>(sensor);
    }
    
    public FunctionPollConfig(AttributeSensor<T> sensor) {
        super(sensor);
    }

    public FunctionPollConfig(FunctionPollConfig<S, T> other) {
        super(other);
        callable = other.callable;
    }
    
    public Callable<? extends Object> getCallable() {
        return callable;
    }
    
    /**
     * The {@link Callable} to be invoked on each poll.
     * <p>
     * Note this <em>must</em> use generics, otherwise the return type of subsequent chained
     * calls will (e.g. to {@link FeedConfig#onException(com.google.common.base.Function)} will
     * return the wrong type.
     */
    @SuppressWarnings("unchecked")
    public <newS> FunctionPollConfig<newS, T> callable(Callable<? extends newS> val) {
        this.callable = checkNotNull(val, "callable");
        return (FunctionPollConfig<newS, T>) this;
    }
    
    /**
     * Supplies the value to be returned by each poll.
     * <p>
     * Note this <em>must</em> use generics, otherwise the return type of subsequent chained
     * calls will (e.g. to {@link FeedConfig#onException(com.google.common.base.Function)} will
     * return the wrong type.
     */
    @SuppressWarnings("unchecked")
    public <newS> FunctionPollConfig<newS, T> supplier(final Supplier<? extends newS> val) {
        this.callable = Functionals.callable( checkNotNull(val, "supplier") );
        return (FunctionPollConfig<newS, T>) this;
    }
    
    /** @deprecated since 0.7.0, kept for legacy compatibility when deserializing */
    @SuppressWarnings({ "unchecked", "unused" })
    private <newS> FunctionPollConfig<newS, T> supplierLegacy(final Supplier<? extends newS> val) {
        checkNotNull(val, "supplier");
        this.callable = new Callable<newS>() {
            @Override
            public newS call() throws Exception {
                return val.get();
            }
        };
        return (FunctionPollConfig<newS, T>) this;
    }

    public FunctionPollConfig<S, T> closure(Closure<?> val) {
        this.callable = GroovyJavaMethods.callableFromClosure(checkNotNull(val, "closure"));
        return this;
    }

    @Override protected String toStringBaseName() { return "fn"; }
    @Override protected String toStringPollSource() {
        if (callable==null) return null;
        String cs = callable.toString();
        if (!cs.contains( ""+Integer.toHexString(callable.hashCode()) )) {
            return cs;
        }
        // if hashcode is in callable it's probably a custom internal; return class name
        return JavaClassNames.simpleClassName(callable);
    }

}

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

import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.PollConfig;
import brooklyn.util.GroovyJavaMethods;

import com.google.common.base.Supplier;

public class FunctionPollConfig<S, T> extends PollConfig<S, T, FunctionPollConfig<S, T>> {

    private Callable<?> callable;
    
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
    
    public FunctionPollConfig<S, T> callable(Callable<? extends S> val) {
        this.callable = checkNotNull(val, "callable");
        return this;
    }
    
    public FunctionPollConfig<S, T> supplier(final Supplier<? extends S> val) {
        checkNotNull(val, "supplier");
        this.callable = new Callable<S>() {
            @Override
            public S call() throws Exception {
                return val.get();
            }
        };
        return this;
    }

    public FunctionPollConfig<S, T> closure(Closure<?> val) {
        this.callable = GroovyJavaMethods.callableFromClosure(checkNotNull(val, "closure"));
        return this;
    }
}

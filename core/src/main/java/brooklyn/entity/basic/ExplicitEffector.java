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
package brooklyn.entity.basic;

import groovy.lang.Closure;

import java.util.List;
import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.ParameterType;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

public abstract class ExplicitEffector<I,T> extends AbstractEffector<T> {
    public ExplicitEffector(String name, Class<T> type, String description) {
        this(name, type, ImmutableList.<ParameterType<?>>of(), description);
    }
    public ExplicitEffector(String name, Class<T> type, List<ParameterType<?>> parameters, String description) {
        super(name, type, parameters, description);
    }

    public T call(Entity entity, Map parameters) {
        return invokeEffector((I) entity, parameters );
    }

    public abstract T invokeEffector(I trait, Map<String,?> parameters);
    
    /** convenience to create an effector supplying a closure; annotations are preferred,
     * and subclass here would be failback, but this is offered as 
     * workaround for bug GROOVY-5122, as discussed in test class CanSayHi 
     */
    public static <I,T> ExplicitEffector<I,T> create(String name, Class<T> type, List<ParameterType<?>> parameters, String description, Closure body) {
        return new ExplicitEffectorFromClosure<I,T>(name, type, parameters, description, body);
    }
    
    private static class ExplicitEffectorFromClosure<I,T> extends ExplicitEffector<I,T> {
        private static final long serialVersionUID = -5771188171702382236L;
        final Closure<T> body;
        public ExplicitEffectorFromClosure(String name, Class<T> type, List<ParameterType<?>> parameters, String description, Closure<T> body) {
            super(name, type, parameters, description);
            this.body = body;
        }
        public T invokeEffector(I trait, Map<String,?> parameters) { return body.call(trait, parameters); }
        
        @Override
        public int hashCode() {
            return Objects.hashCode(super.hashCode(), body);
        }
        
        @Override
        public boolean equals(Object other) {
            return super.equals(other) && Objects.equal(body, ((ExplicitEffectorFromClosure<?,?>)other).body);
        }
        
    }
}

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
package org.apache.brooklyn.util.groovy;

import groovy.lang.Closure;

import com.google.common.base.Function;

public class FromFunctionClosure<T> extends Closure<T> {
    private static final long serialVersionUID = 1L;
    private Function<Object, T> job;

    @SuppressWarnings("unchecked")
    public FromFunctionClosure(Class<GroovyJavaMethods> owner, Function<?, T> job) {
        super(owner, owner);
        this.job = (Function<Object, T>) job;
    }

    public T doCall(Object it) throws Exception {
        return job.apply(it);
    }

}

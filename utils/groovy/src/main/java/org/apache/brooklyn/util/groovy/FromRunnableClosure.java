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

import java.util.concurrent.Callable;

import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;

public class FromRunnableClosure<T> extends Closure<T> {
    private static final long serialVersionUID = 1L;
    private Runnable job;

    public FromRunnableClosure(Class<GroovyJavaMethods> owner, Runnable job) {
        super(owner, owner);
        this.job = job;
    }

    @SuppressWarnings("unchecked")
    public T doCall() throws Throwable {
        if (ScriptBytecodeAdapter.isCase(job, Callable.class)) {
            return ((Callable<T>)job).call();
        } else {
            job.run();
            return null;
        }
    }

}

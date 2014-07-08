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
package brooklyn.util.task;

import groovy.lang.Closure;

import java.util.concurrent.Callable;

import com.google.common.base.Function;
import com.google.common.base.Throwables;

public class ExecutionUtils {
    /**
     * Attempts to run/call the given object, with the given arguments if possible, preserving the return value if there is one (null otherwise);
     * throws exception if the callable is a non-null object which cannot be invoked (not a callable or runnable)
     * @deprecated since 0.7.0 ; this super-loose typing should be avoided; if it is needed, let's move it to one of the Groovy compatibility classes
     */
    public static Object invoke(Object callable, Object ...args) {
        if (callable instanceof Closure) return ((Closure<?>)callable).call(args);
        if (callable instanceof Callable) {
            try {
                return ((Callable<?>)callable).call();
            } catch (Throwable t) {
                throw Throwables.propagate(t);
            }
        }
        if (callable instanceof Runnable) { ((Runnable)callable).run(); return null; }
        if (callable instanceof Function && args.length == 1) { return ((Function)callable).apply(args[0]); }
        if (callable==null) return null;
        throw new IllegalArgumentException("Cannot invoke unexpected object "+callable+" of type "+callable.getClass()+", with "+args.length+" args");
    }
}

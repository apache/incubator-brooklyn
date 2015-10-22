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

import java.util.concurrent.Callable;

import org.apache.brooklyn.util.concurrent.CallableFromRunnable;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.codehaus.groovy.runtime.callsite.CallSiteArray;
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;

import com.google.common.base.Function;
import com.google.common.base.Predicate;

import groovy.lang.Closure;
import groovy.lang.GString;

/** handy methods available in groovy packaged so they can be consumed from java,
 *  and other conversion/conveniences; but see JavaGroovyEquivalents for faster alternatives */
public class GroovyJavaMethods {
    private static final CallSiteArray CALL_SITE_ARRAY = new CallSiteArray(GroovyJavaMethods.class, new String[] {"metaClass", "invokeMethod"});

    //TODO use named subclasses, would that be more efficient?

    // TODO xFromY methods not in correct class: they are not "handy method available in groovy"?
    public static <T> Closure<T> closureFromRunnable(final Runnable job) {
        return new FromRunnableClosure<T>(GroovyJavaMethods.class, job);
    }
    
    public static <T> Closure<T> closureFromCallable(final Callable<T> job) {
        return new FromCallableClosure<T>(GroovyJavaMethods.class, job);
    }

    public static <T> Closure<T> closureFromFunction(final Function<?,T> job) {
        return new FromFunctionClosure<T>(GroovyJavaMethods.class, job);
    }

    @SuppressWarnings("unchecked")
    public static <T> Callable<T> callableFromClosure(final Closure<T> job) {
        try {
            return (Callable<T>)ScriptBytecodeAdapter.asType(job, Callable.class);
        } catch (Throwable e) {
            throw Exceptions.propagate(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> Callable<T> callableFromRunnable(final Runnable job) {
        try {
            if (ScriptBytecodeAdapter.isCase(job, Callable.class)) {
                return (Callable<T>)ScriptBytecodeAdapter.asType(job, Callable.class);
            } else {
                return CallableFromRunnable.newInstance(job, null);
            }
        } catch (Throwable e) {
            throw Exceptions.propagate(e);
        }
    }

    public static <T> Predicate<T> predicateFromClosure(final Closure<Boolean> job) {
        return new Predicate<T>() {
            @Override
            public boolean apply(Object input) {
                return job.call(input);
            }
        };
    }

    public static <F,T> Function<F,T> functionFromClosure(final Closure<T> job) {
        return new Function<F,T>() {
            @Override
            public T apply(F input) {
                return job.call(input);
            }
        };
    }

    @SuppressWarnings("unchecked")
    public static <T> Predicate<T> castToPredicate(Object o) {
        try {
            if (ScriptBytecodeAdapter.isCase(o, Closure.class)) {
                return predicateFromClosure((Closure<Boolean>)o);
            } else {
                return (Predicate<T>) o;
            }
        } catch (Throwable e) {
            throw Exceptions.propagate(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> Closure<T> castToClosure(Object o) {
        try {
            if (ScriptBytecodeAdapter.compareEqual(o, null)) {
                return (Closure<T>)ScriptBytecodeAdapter.castToType(o, Closure.class);
            } else if (ScriptBytecodeAdapter.isCase(o, Closure.class)) {
                return (Closure<T>)ScriptBytecodeAdapter.castToType(o, Closure.class);
            } else if (o instanceof Runnable) {
                return closureFromRunnable((Runnable)ScriptBytecodeAdapter.createPojoWrapper(ScriptBytecodeAdapter.castToType(o, Runnable.class), Runnable.class));
            } else if (o instanceof Callable) {
                return closureFromCallable((Callable<T>)ScriptBytecodeAdapter.createPojoWrapper(ScriptBytecodeAdapter.castToType(o, Callable.class), Callable.class));
            } else if (o instanceof Function) {
                return closureFromFunction((Function<Object, T>)ScriptBytecodeAdapter.createPojoWrapper(ScriptBytecodeAdapter.castToType(o, Function.class), Function.class));
            } else {
                throw new IllegalArgumentException("Cannot convert to closure: o="+o+"; type="+(o != null ? o.getClass() : null));
            }
        } catch (Throwable e) {
            throw Exceptions.propagate(e);
        }
    }

/* alternatives to above; but I think the above is more efficient?  (even more efficient if moved from java to groovy)  --alex jun 2012
    public static <K,T> Function<K,T> functionFromClosure(final Closure<T> job) {
        return job as Function;
    }

    public static <T> Predicate<T> predicateFromClosure(final Closure<Boolean> job) {
        return job as Predicate;
    }
*/
    
    public static Predicate<Object> truthPredicate() {
        return new Predicate<Object>() {
           @Override public boolean apply(Object input) {
               return truth(input);
           }
        };
    }
    
    public static boolean truth(Object o) {
        if (DefaultTypeTransformation.booleanUnbox(o)) return true;
        return false;
    }

    public static <T> T elvis(Object preferred, Object fallback) {
        try {
            return fix(truth(preferred) ? preferred : fallback);
        } catch (Throwable e) {
            throw Exceptions.propagate(e);
        }
    }
    
    public static <T> T elvis(Object... preferences) {
        try {
            if (preferences.length == 0) throw new IllegalArgumentException("preferences must not be empty for elvis");
            for (Object contender : preferences) {
                if (truth(contender)) return fix(contender);
            }
            return fix(preferences[preferences.length-1]);
        } catch (Throwable e) {
            throw Exceptions.propagate(e);
        }
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T fix(Object o) {
        try {
            if (ScriptBytecodeAdapter.isCase(o, GString.class)) {
                return (T)ScriptBytecodeAdapter.asType(o, String.class);
            } else {
                return (T)o;
            }
        } catch (Throwable e) {
            throw Exceptions.propagate(e);
        }
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T invokeMethodOnMetaClass(Object target, String methodName, Object args) {
        try {
            CallSite[] callSiteArray = getCallSiteArray();
            Object metaClass = callSiteArray[0].callGetProperty(target);
            return (T) callSiteArray[1].call(metaClass, target, methodName, args);
        } catch (Throwable e) {
            throw Exceptions.propagate(e);
        }
    }

    private static CallSite[] getCallSiteArray() {
        return CALL_SITE_ARRAY.array;
    }
}

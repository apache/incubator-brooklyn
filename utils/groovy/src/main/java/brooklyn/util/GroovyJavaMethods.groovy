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
package brooklyn.util;

import static brooklyn.util.GroovyJavaMethods.truth

import java.util.concurrent.Callable

import brooklyn.util.concurrent.CallableFromRunnable;

import com.google.common.base.Function
import com.google.common.base.Predicate

/** handy methods available in groovy packaged so they can be consumed from java,
 *  and other conversion/conveniences; but see JavaGroovyEquivalents for faster alternatives */
public class GroovyJavaMethods {

    //TODO use named subclasses, would that be more efficient?

    // TODO xFromY methods not in correct class: they are not "handy method available in groovy"?
    public static Closure closureFromRunnable(final Runnable job) {
        return {
            if (job in Callable) { return job.call() }
            else { job.run(); null; }
        };
    }
    
    public static Closure closureFromCallable(final Callable job) {
        return { job.call(); };
    }

    public static <T> Closure<T> closureFromFunction(final Function<?,T> job) {
        return { it -> return job.apply(it); };
    }

    public static <T> Callable<T> callableFromClosure(final Closure<T> job) {
        return job as Callable;
    }

    public static <T> Callable<T> callableFromRunnable(final Runnable job) {
        return (job in Callable) ? callableFromClosure(job) : CallableFromRunnable.newInstance(job, null);
    }

    public static <T> Predicate<T> predicateFromClosure(final Closure<Boolean> job) {
        // TODO using `Predicate<T>` on the line below gives "unable to resolve class T"
        return new Predicate<Object>() {
            public boolean apply(Object input) {
                return job.call(input);
            }
        };
    }

    public static <F,T> Function<F,T> functionFromClosure(final Closure<T> job) {
        // TODO using `Function<F,T>` on the line below gives "unable to resolve class T"
        return new Function<Object,Object>() {
            public Object apply(Object input) {
                return job.call(input);
            }
        };
    }

    public static <T> Predicate<T> castToPredicate(Object o) {
        if (o in Closure) {
            return predicateFromClosure(o);
        } else {
            return (Predicate<T>) o;
        }
    }

    public static <T> Closure castToClosure(Object o) {
        if (o == null) {
            return o;
        } else if (o in Closure) {
            return o;
        } else if (o instanceof Runnable) {
            return closureFromRunnable((Runnable)o);
        } else if (o instanceof Callable) {
            return closureFromCallable((Callable)o); 
        } else if (o instanceof Function) {
            return closureFromFunction((Function)o); 
        } else {
            throw new IllegalArgumentException("Cannot convert to closure: o="+o+"; type="+(o != null ? o.getClass() : null));
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
        if (o) return true;
        return false;
    }

    public static <T> T elvis(Object preferred, Object fallback) {
        return fix(preferred ?: fallback);
    }
    
    public static <T> T elvis(Object... preferences) {
        if (preferences.length == 0) throw new IllegalArgumentException("preferences must not be empty for elvis");
        for (Object contender : preferences) {
            if (contender) return fix(contender);
        }
        return fix(preferences[preferences.size()-1]);
    }
    
    public static <T> T fix(Object o) {
        if (o in GString) return (o as String);
        return o;
    }
    
    // args is expected to be an array, but for groovy compilation reasons it's not declared as such in the signature :-(
    public static <T> T invokeMethodOnMetaClass(Object target, String methodName, Object args) {
        return target.metaClass.invokeMethod(target, methodName, args);
    }
}

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
package brooklyn.util.exceptions;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.instanceOf;
import static com.google.common.base.Throwables.getCausalChain;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

import brooklyn.util.text.Strings;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class Exceptions {

    private static final List<Class<? extends Throwable>> BORING_THROWABLE_SUPERTYPES = ImmutableList.<Class<? extends Throwable>>of(
        ExecutionException.class, InvocationTargetException.class, PropagatedRuntimeException.class);

    private static boolean isBoring(Throwable t) {
        for (Class<? extends Throwable> type: BORING_THROWABLE_SUPERTYPES)
            if (type.isInstance(t)) return true;
        return false;
    }

    private static final Predicate<Throwable> IS_THROWABLE_BORING = new Predicate<Throwable>() {
        @Override
        public boolean apply(Throwable input) {
            return isBoring(input);
        }
    };

    private static List<Class<? extends Throwable>> BORING_PREFIX_THROWABLE_EXACT_TYPES = ImmutableList.<Class<? extends Throwable>>of(
        IllegalStateException.class, RuntimeException.class, CompoundRuntimeException.class);

    /** Returns whether this is throwable either known to be boring or to have an unuseful prefix */
    public static boolean isPrefixBoring(Throwable t) {
        if (isBoring(t))
            return true;
        for (Class<? extends Throwable> type: BORING_PREFIX_THROWABLE_EXACT_TYPES)
            if (t.getClass().equals(type)) return true;
        return false;
    }

    private static String stripBoringPrefixes(String s) {
        String was;
        do {
            was = s;
            for (Class<? extends Throwable> type: BORING_PREFIX_THROWABLE_EXACT_TYPES) {
                s = Strings.removeAllFromStart(type.getCanonicalName(), type.getName(), type.getSimpleName(), ":", " ");
            }
        } while (!was.equals(s));
        return s;
    }

    /**
     * Propagate a {@link Throwable} as a {@link RuntimeException}.
     * <p>
     * Like Guava {@link Throwables#propagate(Throwable)} but:
     * <li> throws {@link RuntimeInterruptedException} to handle {@link InterruptedException}s; and
     * <li> wraps as PropagatedRuntimeException for easier filtering
     */
    public static RuntimeException propagate(Throwable throwable) {
        if (throwable instanceof InterruptedException)
            throw new RuntimeInterruptedException((InterruptedException) throwable);
        Throwables.propagateIfPossible(checkNotNull(throwable));
        throw new PropagatedRuntimeException(throwable);
    }

    /** 
     * Propagate exceptions which are fatal.
     * <p>
     * Propagates only those exceptions which one rarely (if ever) wants to capture,
     * such as {@link InterruptedException} and {@link Error}s.
     */
    public static void propagateIfFatal(Throwable throwable) {
        if (throwable instanceof InterruptedException)
            throw new RuntimeInterruptedException((InterruptedException) throwable);
        if (throwable instanceof RuntimeInterruptedException)
            throw (RuntimeInterruptedException) throwable;
        if (throwable instanceof Error)
            throw (Error) throwable;
    }

    /** returns the first exception of the given type, or null */
    @SuppressWarnings("unchecked")
    public static <T extends Throwable> T getFirstThrowableOfType(Throwable from, Class<T> clazz) {
        return (T) Iterables.tryFind(getCausalChain(from), instanceOf(clazz)).orNull();
    }

    /** returns the first exception in the call chain which is not of common uninteresting types
     * (ie excluding ExecutionException and PropagatedRuntimeExceptions); 
     * or the original throwable if all are uninteresting 
     */
    public static Throwable getFirstInteresting(Throwable throwable) {
        return Iterables.tryFind(getCausalChain(throwable), Predicates.not(IS_THROWABLE_BORING)).or(throwable);
    }

    /** creates (but does not throw) a new {@link PropagatedRuntimeException} whose 
     * message and cause are taken from the first _interesting_ element in the source */
    public static Throwable collapse(Throwable source) {
        return collapse(source, true);
    }
    
    /** creates (but does not throw) a new {@link PropagatedRuntimeException} whose 
     * message is taken from the first _interesting_ element in the source,
     * and optionally also the causal chain */
    public static Throwable collapse(Throwable source, boolean collapseCausalChain) {
        String message = "";
        Throwable collapsed = source;
        int collapseCount = 0;
        // remove boring stack traces at the head
        while (isBoring(collapsed)) {
            collapseCount++;
            Throwable cause = collapsed.getCause();
            if (cause==null)
                // everything in the tree is boring...
                return source;
            String collapsedS = collapsed.getMessage();
            if (Strings.isNonBlank(collapsedS)) {
                collapsedS = Strings.removeFromEnd(collapsedS, cause.toString(), stripBoringPrefixes(cause.toString()), cause.getMessage());
                collapsedS = stripBoringPrefixes(collapsedS);
                if (Strings.isNonBlank(collapsedS))
                    message = appendSeparator(message, collapsedS);
            }
            collapsed = cause;
        }
        // if no messages so far (ie we will be the toString) then remove boring prefixes from the message
        Throwable messagesCause = collapsed;
        while (isPrefixBoring(messagesCause) && Strings.isBlank(message)) {
            collapseCount++;
            if (Strings.isNonBlank(messagesCause.getMessage())) {
                message = messagesCause.getMessage();
                break;
            }
            messagesCause = messagesCause.getCause();
        }
        if (collapseCount==0)
            return source;
        if (Strings.isBlank(message))
            return new PropagatedRuntimeException(collapseCausalChain ? collapsed : source);
        else
            return new PropagatedRuntimeException(message, collapseCausalChain ? collapsed : source, true);
    }
    
    static String appendSeparator(String message, String next) {
        if (Strings.isBlank(message))
            return next;
        if (message.trim().endsWith(":") || message.trim().endsWith(";"))
            return message.trim()+" "+next;
        return message + ": " + next;
    }

    /** removes uninteresting items from the top of the call stack (but keeps interesting messages), and throws 
     * @deprecated since 0.7.0 same as {@link #propagate(Throwable)} */
    public static RuntimeException propagateCollapsed(Throwable source) {
        throw propagate(source);
    }

    /** like {@link #collapse(Throwable)} but returning a one-line message suitable for logging without traces */
    public static String collapseText(Throwable t) {
        if (t == null) return null;
        Throwable t2 = collapse(t);
        if (t2 instanceof PropagatedRuntimeException) {
            if (((PropagatedRuntimeException)t2).isCauseEmbeddedInMessage())
                // normally
                return t2.getMessage();
            else if (t2.getCause()!=null)
                return ""+t2.getCause();
            return ""+t2.getClass();
        }
        return t2.toString();
    }

    public static RuntimeException propagate(String prefix, Collection<? extends Throwable> exceptions) {
        if (exceptions.size()==1)
            throw new CompoundRuntimeException(prefix + ": " + Exceptions.collapseText(exceptions.iterator().next()), exceptions);
        if (exceptions.isEmpty())
            throw new CompoundRuntimeException(prefix, exceptions);
        throw new CompoundRuntimeException(prefix + ", including: " + Exceptions.collapseText(exceptions.iterator().next()), exceptions);
    }

    public static RuntimeException propagate(Collection<? extends Throwable> exceptions) {
        throw propagate(create(exceptions));
    }

    /** creates the given exception, but without propagating it, for use when caller will be wrapping */
    public static Throwable create(Collection<? extends Throwable> exceptions) {
        if (exceptions.size()==1)
            return exceptions.iterator().next();
        if (exceptions.isEmpty())
            return new CompoundRuntimeException("(empty compound exception)", exceptions);
        return new CompoundRuntimeException(exceptions.size()+" errors, including: " + Exceptions.collapseText(exceptions.iterator().next()), exceptions);
    }

}

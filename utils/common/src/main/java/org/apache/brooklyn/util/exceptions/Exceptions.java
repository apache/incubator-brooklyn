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
package org.apache.brooklyn.util.exceptions;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.instanceOf;
import static com.google.common.base.Throwables.getCausalChain;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nullable;

import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.text.Strings;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class Exceptions {

    private static final List<Class<? extends Throwable>> BORING_THROWABLE_SUPERTYPES = ImmutableList.<Class<? extends Throwable>>of(
        ExecutionException.class, InvocationTargetException.class, PropagatedRuntimeException.class, UndeclaredThrowableException.class);

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

    /** Returns whether this is throwable either known to be boring or to have an unhelpful type name (prefix)
     * which should be suppressed. null is accepted but treated as not boring. */
    public static boolean isPrefixBoring(Throwable t) {
        if (t==null) return false;
        if (isBoring(t))
            return true;
        if (t instanceof UserFacingException) return true;
        for (Class<? extends Throwable> type: BORING_PREFIX_THROWABLE_EXACT_TYPES)
            if (t.getClass().equals(type)) return true;
        return false;
    }

    private static String stripBoringPrefixes(String s) {
        ArrayList<String> prefixes = Lists.newArrayListWithCapacity(2 + BORING_PREFIX_THROWABLE_EXACT_TYPES.size() * 3);
        for (Class<? extends Throwable> type : BORING_PREFIX_THROWABLE_EXACT_TYPES) {
            prefixes.add(type.getCanonicalName());
            prefixes.add(type.getName());
            prefixes.add(type.getSimpleName());
        }
        prefixes.add(":");
        prefixes.add(" ");
        String[] ps = prefixes.toArray(new String[prefixes.size()]);
        return Strings.removeAllFromStart(s, ps);
    }

    /**
     * Propagate a {@link Throwable} as a {@link RuntimeException}.
     * <p>
     * Like Guava {@link Throwables#propagate(Throwable)} but:
     * <li> throws {@link RuntimeInterruptedException} to handle {@link InterruptedException}s; and
     * <li> wraps as PropagatedRuntimeException for easier filtering
     */
    public static RuntimeException propagate(Throwable throwable) {
        if (throwable instanceof InterruptedException) {
            throw new RuntimeInterruptedException((InterruptedException) throwable);
        } else if (throwable instanceof RuntimeInterruptedException) {
            Thread.currentThread().interrupt();
            throw (RuntimeInterruptedException) throwable;
        }
        Throwables.propagateIfPossible(checkNotNull(throwable));
        throw new PropagatedRuntimeException(throwable);
    }

    /**
     * See {@link #propagate(Throwable)}. If wrapping the exception, then include the given message;
     * otherwise the message is not used.
     */
    public static RuntimeException propagate(String msg, Throwable throwable) {
        if (throwable instanceof InterruptedException) {
            throw new RuntimeInterruptedException(msg, (InterruptedException) throwable);
        } else if (throwable instanceof RuntimeInterruptedException) {
            Thread.currentThread().interrupt();
            throw (RuntimeInterruptedException) throwable;
        }
        Throwables.propagateIfPossible(checkNotNull(throwable));
        throw new PropagatedRuntimeException(msg, throwable);
    }
    
    /** 
     * Propagate exceptions which are fatal.
     * <p>
     * Propagates only those exceptions which one rarely (if ever) wants to capture,
     * such as {@link InterruptedException} and {@link Error}s.
     */
    public static void propagateIfFatal(Throwable throwable) {
        if (throwable instanceof InterruptedException) {
            throw new RuntimeInterruptedException((InterruptedException) throwable);
        } else if (throwable instanceof RuntimeInterruptedException) {
            Thread.currentThread().interrupt();
            throw (RuntimeInterruptedException) throwable;
        } else if (throwable instanceof Error) {
            throw (Error) throwable;
        }
    }

    /** returns the first exception of the given type, or null */
    @SuppressWarnings("unchecked")
    public static <T extends Throwable> T getFirstThrowableOfType(Throwable from, Class<T> clazz) {
        return (T) Iterables.tryFind(getCausalChain(from), instanceOf(clazz)).orNull();
    }

    /** returns the first exception that matches the filter, or null */
    public static Throwable getFirstThrowableMatching(Throwable from, Predicate<? super Throwable> filter) {
        return Iterables.tryFind(getCausalChain(from), filter).orNull();
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
    
    /** as {@link #collapse(Throwable)} but includes causal messages in the message as per {@link #collapseTextIncludingAllCausalMessages(Throwable)};
     * use with care (limit once) as repeated usage can result in multiple copies of the same message */ 
    public static Throwable collapseIncludingAllCausalMessages(Throwable source) {
        return collapse(source, true, true, ImmutableSet.<Throwable>of());
    }
    
    /** creates (but does not throw) a new {@link PropagatedRuntimeException} whose 
     * message is taken from the first _interesting_ element in the source,
     * and optionally also the causal chain */
    public static Throwable collapse(Throwable source, boolean collapseCausalChain) {
        return collapse(source, collapseCausalChain, false, ImmutableSet.<Throwable>of());
    }
    
    private static Throwable collapse(Throwable source, boolean collapseCausalChain, boolean includeAllCausalMessages, Set<Throwable> visited) {
        visited = MutableSet.copyOf(visited);
        String message = "";
        Throwable collapsed = source;
        int collapseCount = 0;
        boolean messageIsFinal = false;
        // remove boring stack traces at the head
        while (isBoring(collapsed)  && !messageIsFinal) {
            collapseCount++;
            Throwable cause = collapsed.getCause();
            if (cause==null) {
                // everything in the tree is boring...
                return source;
            }
            if (visited.add(collapsed)) {
                // there is a recursive loop
                break;
            }
            String collapsedS = collapsed.getMessage();
            if (collapsed instanceof PropagatedRuntimeException && ((PropagatedRuntimeException)collapsed).isCauseEmbeddedInMessage()) {
                message = collapsed.getMessage();
                messageIsFinal = true;
            } else if (Strings.isNonBlank(collapsedS)) {
                collapsedS = Strings.removeAllFromEnd(collapsedS, cause.toString(), stripBoringPrefixes(cause.toString()), cause.getMessage());
                collapsedS = stripBoringPrefixes(collapsedS);
                if (Strings.isNonBlank(collapsedS))
                    message = appendSeparator(message, collapsedS);
            }
            collapsed = cause;
        }
        // if no messages so far (ie we will be the toString) then remove boring prefixes from the message
        Throwable messagesCause = collapsed;
        while (messagesCause!=null && isPrefixBoring(messagesCause) && Strings.isBlank(message)) {
            collapseCount++;
            if (Strings.isNonBlank(messagesCause.getMessage())) {
                message = messagesCause.getMessage();
                messagesCause = messagesCause.getCause();
                break;
            }
            visited.add(messagesCause); messagesCause = messagesCause.getCause();
        }
        
        if (collapseCount==0 && !includeAllCausalMessages)
            return source;
        
        if (collapseCount==0 && messagesCause!=null) {
            message = messagesCause.toString();
            messagesCause = messagesCause.getCause();
        }
        
        if (messagesCause!=null && !messageIsFinal) {
            String extraMessage = collapseText(messagesCause, includeAllCausalMessages, ImmutableSet.copyOf(visited));
            message = appendSeparator(message, extraMessage);
        }
        if (message==null) message = "";
        return new PropagatedRuntimeException(message, collapseCausalChain ? collapsed : source, true);
    }
    
    static String appendSeparator(String message, String next) {
        if (Strings.isBlank(message))
            return next;
        if (Strings.isBlank(next))
            return message;
        if (message.endsWith(next))
            return message;
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
        return collapseText(t, false);
    }

    /** normally {@link #collapseText(Throwable)} will stop following causal chains when encountering an interesting exception
     * with a message; this variant will continue to follow such causal chains, showing all messages. 
     * for use e.g. when verbose is desired in the single-line message. */
    public static String collapseTextIncludingAllCausalMessages(Throwable t) {
        return collapseText(t, true);
    }
    
    private static String collapseText(Throwable t, boolean includeAllCausalMessages) {
        return collapseText(t, includeAllCausalMessages, ImmutableSet.<Throwable>of());
    }
    private static String collapseText(Throwable t, boolean includeAllCausalMessages, Set<Throwable> visited) {
        if (t == null) return null;
        if (visited.contains(t)) {
            // IllegalStateException sometimes refers to itself; guard against stack overflows
            if (Strings.isNonBlank(t.getMessage())) return t.getMessage();
            else return "("+t.getClass().getName()+", recursive cause)";
        }
        Throwable t2 = collapse(t, true, includeAllCausalMessages, visited);
        visited = MutableSet.copyOf(visited);
        visited.add(t);
        visited.add(t2);
        if (t2 instanceof PropagatedRuntimeException) {
            if (((PropagatedRuntimeException)t2).isCauseEmbeddedInMessage())
                // normally
                return t2.getMessage();
            else if (t2.getCause()!=null)
                return collapseText(t2.getCause(), includeAllCausalMessages, ImmutableSet.copyOf(visited));
            return ""+t2.getClass();
        }
        String result = t2.toString();
        if (!includeAllCausalMessages) {
            return result;
        }
        Throwable cause = t2.getCause();
        if (cause != null) {
            String causeResult = collapseText(new PropagatedRuntimeException(cause));
            if (result.indexOf(causeResult)>=0)
                return result;
            return result + "; caused by "+causeResult;
        }
        return result;
    }

    public static RuntimeException propagate(Collection<? extends Throwable> exceptions) {
        throw propagate(create(exceptions));
    }
    public static RuntimeException propagate(String prefix, Collection<? extends Throwable> exceptions) {
        throw propagate(create(prefix, exceptions));
    }

    /** creates the given exception, but without propagating it, for use when caller will be wrapping */
    public static Throwable create(Collection<? extends Throwable> exceptions) {
        return create(null, exceptions);
    }
    /** creates the given exception, but without propagating it, for use when caller will be wrapping */
    public static RuntimeException create(@Nullable String prefix, Collection<? extends Throwable> exceptions) {
        if (exceptions.size()==1) {
            Throwable e = exceptions.iterator().next();
            if (Strings.isBlank(prefix)) return new PropagatedRuntimeException(e);
            return new PropagatedRuntimeException(prefix + ": " + Exceptions.collapseText(e), e);
        }
        if (exceptions.isEmpty()) {
            if (Strings.isBlank(prefix)) return new CompoundRuntimeException("(empty compound exception)", exceptions);
            return new CompoundRuntimeException(prefix, exceptions);
        }
        if (Strings.isBlank(prefix)) return new CompoundRuntimeException(exceptions.size()+" errors, including: " + Exceptions.collapseText(exceptions.iterator().next()), exceptions);
        return new CompoundRuntimeException(prefix+", "+exceptions.size()+" errors including: " + Exceptions.collapseText(exceptions.iterator().next()), exceptions);
    }

}

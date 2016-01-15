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
package org.apache.brooklyn.feed.ssh;

import javax.annotation.Nullable;

import org.apache.brooklyn.util.guava.Functionals;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicates;

public class SshValueFunctions {

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<SshPollValue, Integer> exitStatusOld() {
        // TODO PERSISTENCE WORKAROUND
        return new Function<SshPollValue, Integer>() {
            @Override public Integer apply(SshPollValue input) {
                return input.getExitStatus();
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<SshPollValue, String> stdoutOld() {
        // TODO PERSISTENCE WORKAROUND
        return new Function<SshPollValue, String>() {
            @Override public String apply(SshPollValue input) {
                return input.getStdout();
            }
        };
    }
    
    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<SshPollValue, String> stderrOld() {
        // TODO PERSISTENCE WORKAROUND
        return new Function<SshPollValue, String>() {
            @Override public String apply(SshPollValue input) {
                return input.getStderr();
            }
        };
    }
    
    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static <A,B,C> Function<A,C> chainOld(final Function<A,? extends B> f1, final Function<B,C> f2) {
        // TODO PERSISTENCE WORKAROUND
        return new Function<A,C>() {
            @Override public C apply(@Nullable A input) {
                return f2.apply(f1.apply(input));
            }
        };
    }
    
    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static <A,B,C,D> Function<A,D> chainOld(final Function<A,? extends B> f1, final Function<B,? extends C> f2, final Function<C,D> f3) {
        // TODO PERSISTENCE WORKAROUND
        return new Function<A,D>() {
            @Override public D apply(@Nullable A input) {
                return f3.apply(f2.apply(f1.apply(input)));
            }
        };
    }

    public static Function<SshPollValue, Integer> exitStatus() {
        return new ExitStatus();
    }

    protected static class ExitStatus implements Function<SshPollValue, Integer> {
        @Override public Integer apply(SshPollValue input) {
            return input.getExitStatus();
        }
    }

    public static Function<SshPollValue, String> stdout() {
        return new Stdout();
    }

    protected static class Stdout implements Function<SshPollValue, String> {
        @Override public String apply(SshPollValue input) {
            return input.getStdout();
        }
    }

    public static Function<SshPollValue, String> stderr() {
        return new Stderr();
    }

    protected static class Stderr implements Function<SshPollValue, String> {
        @Override public String apply(SshPollValue input) {
            return input.getStderr();
        }
    }

    public static Function<SshPollValue, Boolean> exitStatusEquals(final int expected) {
        return chain(SshValueFunctions.exitStatus(), Functions.forPredicate(Predicates.equalTo(expected)));
    }

    /**
     * @deprecated since 0.9.0; use {@link Functionals#chain(Function, Function)}
     */
    public static <A,B,C> Function<A,C> chain(final Function<A,? extends B> f1, final Function<B,C> f2) {
        return Functionals.chain(f1, f2);
    }

    /**
     * @deprecated since 0.9.0; use {@link Functionals#chain(Function, Function, Function)}
     */
    public static <A,B,C,D> Function<A,D> chain(final Function<A,? extends B> f1, final Function<B,? extends C> f2, final Function<C,D> f3) {
        return Functionals.chain(f1, f2, f3);
    }
}

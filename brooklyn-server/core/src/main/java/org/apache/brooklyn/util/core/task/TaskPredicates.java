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
package org.apache.brooklyn.util.core.task;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.brooklyn.api.mgmt.Task;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

public class TaskPredicates {

    /** @deprecated since 0.9.0; use {@link #displayNameSatisfies(Predicate)} */
    public static Predicate<Task<?>> displayNameMatches(Predicate<? super String> matcher) {
        return displayNameSatisfies(matcher);
    }
    
    /**
     * @since 0.9.0
     */
    public static Predicate<Task<?>> displayNameSatisfies(Predicate<? super String> matcher) {
        return new DisplayNameMatches(matcher);
    }
    
    public static Predicate<Task<?>> displayNameEqualTo(String name) {
        return displayNameSatisfies(Predicates.equalTo(name));
    }
    
    private static class DisplayNameMatches implements Predicate<Task<?>> {
        private final Predicate<? super String> matcher;

        public DisplayNameMatches(Predicate<? super String> matcher) {
            this.matcher = checkNotNull(matcher, "matcher");
        }

        @Override
        public boolean apply(Task<?> input) {
            return input != null && matcher.apply(input.getDisplayName());
        }
        
        @Override
        public String toString() {
            return "displayNameMatches("+matcher+")";
        }
    }
    
    public static Predicate<Task<?>> isDone() {
        return new IsDone();
    }
    
    private static class IsDone implements Predicate<Task<?>> {
        @Override
        public boolean apply(Task<?> input) {
            return input.isDone();
        }
        @Override
        public String toString() {
            return "isDone()";
        }
    }
    
}

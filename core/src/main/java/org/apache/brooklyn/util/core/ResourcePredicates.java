/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.brooklyn.util.core;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.core.objs.BrooklynObjectPredicate;
import org.apache.brooklyn.util.text.StringPredicates;
import org.apache.brooklyn.util.text.Strings;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

public class ResourcePredicates {

    private ResourcePredicates() {}

    /**
     * @return A predicate that tests whether its input is a resource readable by Brooklyn.
     * @see ResourceUtils#doesUrlExist(String)
     */
    public static Predicate<String> urlExists() {
        return new ResourceExistsPredicate();
    }

    /**
     * @return A predicate that tests whether its input is either empty or a resource readable by Brooklyn.
     * @see StringPredicates#isBlank
     * @see #urlExists
     */
    public static Predicate<String> urlIsBlankOrExists() {
        return Predicates.or(StringPredicates.isBlank(), urlExists());
    }

    private static class ResourceExistsPredicate implements BrooklynObjectPredicate<String> {

        @Override
        public boolean apply(@Nullable String resource) {
            return apply(resource, null);
        }

        @Override
        public boolean apply(@Nullable String resource, @Nullable BrooklynObject context) {
            return !Strings.isBlank(resource) && new ResourceUtils(context).doesUrlExist(resource);
        }

        @Override
        public String toString() {
            return "ResourcePredicates.exists()";
        }

    }

}

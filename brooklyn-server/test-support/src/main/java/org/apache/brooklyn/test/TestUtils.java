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
package org.apache.brooklyn.test;

import static org.testng.Assert.fail;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper functions for tests of Tomcat, JBoss and others.
 * 
 * @deprecated Since 0.8. Methods moving to {@link Asserts}.
 */
@Deprecated
public class TestUtils {
    private static final Logger log = LoggerFactory.getLogger(TestUtils.class);

    private TestUtils() { }

    /** @deprecated since 0.8; use Asserts.BooleanWithMessage */
    public static class BooleanWithMessage {
        boolean value; String message;
        public BooleanWithMessage(boolean value, String message) {
            this.value = value; this.message = message;
        }
        public boolean asBoolean() {
            return value;
        }
        public String toString() {
            return message;
        }
    }
    
    /** @deprecated since 0.8; use Exceptions.getFirstInteresting */ 
    public static Throwable unwrapThrowable(Throwable t) {
        if (t.getCause() == null) {
            return t;
        } else if (t instanceof ExecutionException) {
            return unwrapThrowable(t.getCause());
        } else if (t instanceof InvokerInvocationException) {
            return unwrapThrowable(t.getCause());
        } else {
            return t;
        }
    }

    /** @deprecated since 0.8; use Asserts.assertEqualsIgnoringOrder */
    public static void assertSetsEqual(Collection c1, Collection c2) {
        Set s = new LinkedHashSet();
        s.addAll(c1); s.removeAll(c2);
        if (!s.isEmpty()) fail("First argument contains additional contents: "+s);
        s.clear(); s.addAll(c2); s.removeAll(c1);
        if (!s.isEmpty()) fail("Second argument contains additional contents: "+s);
    }
    
}

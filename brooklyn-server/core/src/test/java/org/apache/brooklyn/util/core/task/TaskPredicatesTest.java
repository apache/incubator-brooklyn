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

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.apache.brooklyn.api.mgmt.ExecutionManager;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicates;
import com.google.common.util.concurrent.Callables;

public class TaskPredicatesTest extends BrooklynAppUnitTestSupport {

    private ExecutionManager execManager;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        execManager = mgmt.getExecutionManager();
    }

    @Test
    public void testDisplayNameEqualTo() throws Exception {
        Task<Object> task = execManager.submit(TaskBuilder.builder()
                .body(Callables.<Object>returning("val"))
                .displayName("myname")
                .build());
        assertTrue(TaskPredicates.displayNameEqualTo("myname").apply(task));
        assertFalse(TaskPredicates.displayNameEqualTo("wrong").apply(task));
    }
    
    @Test
    public void testDisplayNameMatches() throws Exception {
        Task<Object> task = execManager.submit(TaskBuilder.builder()
                .body(Callables.<Object>returning("val"))
                .displayName("myname")
                .build());
        assertTrue(TaskPredicates.displayNameSatisfies(Predicates.equalTo("myname")).apply(task));
        assertFalse(TaskPredicates.displayNameSatisfies(Predicates.equalTo("wrong")).apply(task));
    }
    
    @Test
    public void testDisplayNameSatisfies() throws Exception {
        Task<Object> task = execManager.submit(TaskBuilder.builder()
                .body(Callables.<Object>returning("val"))
                .displayName("myname")
                .build());
        assertTrue(TaskPredicates.displayNameSatisfies(Predicates.equalTo("myname")).apply(task));
        assertFalse(TaskPredicates.displayNameSatisfies(Predicates.equalTo("wrong")).apply(task));
    }
}

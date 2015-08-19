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
package org.apache.brooklyn.core.entity.lifecycle;

import static org.testng.Assert.assertTrue;

import java.util.Date;

import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle.Transition;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle.TransitionCoalesceFunction;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class LifecycleTransitionTest {
    @DataProvider(name = "states")
    public Object[][] generateLifecycleStates() {
        Object[][] states = new Object[Lifecycle.values().length][];
        int i = 0;
        for (Lifecycle state : Lifecycle.values()) {
            System.out.println(":" + state);
            states[i] = new Object[] {state};
            i++;
        }
        return states;
    }

    @Test(dataProvider="states")
    public void testTransitionCoalesce(Lifecycle state) {
        Transition t = new Transition(state, new Date());
        String serialized = t.toString();
        Transition t2 = new TransitionCoalesceFunction().apply(serialized);
        assertTrue(t.equals(t2), "Deserialized Lifecycle.Transition not equal to original");
    }
}

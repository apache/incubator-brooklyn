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
package brooklyn.entity.trait;

import java.util.Collection;

import org.testng.Assert;

import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.test.entity.TestEntityImpl;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.task.Tasks;

public class FailingEntityImpl extends TestEntityImpl implements FailingEntity {

    public FailingEntityImpl() {
    }
    
    @Override
    public void start(Collection<? extends Location> locs) {
        getConfig(LISTENER).onEvent(this, "start", new Object[] {locs});
        if (getConfig(FAIL_ON_START) || (getConfig(FAIL_ON_START_CONDITION) != null && getConfig(FAIL_ON_START_CONDITION).apply(this))) {
            callHistory.add("start");
            getConfig(EXEC_ON_FAILURE).apply(this);
            throw fail("Simulating entity start failure for test");
        }
        super.start(locs);
    }
    
    @Override
    public void stop() {
        getConfig(LISTENER).onEvent(this, "stop", new Object[0]);
        if (getConfig(FAIL_ON_STOP) || (getConfig(FAIL_ON_STOP_CONDITION) != null && getConfig(FAIL_ON_STOP_CONDITION).apply(this))) {
            callHistory.add("stop");
            getConfig(EXEC_ON_FAILURE).apply(this);
            throw fail("Simulating entity stop failure for test");
        }
        super.stop();
    }
    
    @Override
    public void restart() {
        getConfig(LISTENER).onEvent(this, "restart", new Object[0]);
        if (getConfig(FAIL_ON_RESTART) || (getConfig(FAIL_ON_RESTART_CONDITION) != null && getConfig(FAIL_ON_RESTART_CONDITION).apply(this))) {
            callHistory.add("restart");
            getConfig(EXEC_ON_FAILURE).apply(this);
            throw fail("Simulating entity restart failure for test");
        }
        super.restart();
    }
    
    private RuntimeException fail(final String msg) {
        if (getConfig(FAIL_IN_SUB_TASK)) {
            Task<?> task = Tasks.builder().name(msg).body(new Runnable() { public void run() { throw newException(msg); } }).build();
            Entities.submit(this, task).getUnchecked();
            Assert.fail("Should have thrown exception on task.getUnchecked");
            throw new IllegalStateException("unreachable code");
        } else {
            throw newException(msg);
        }
    }
    
    private RuntimeException newException(String msg) {
        try {
            return getConfig(EXCEPTION_CLAZZ).getConstructor(String.class).newInstance("Simulating entity stop failure for test");
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
}

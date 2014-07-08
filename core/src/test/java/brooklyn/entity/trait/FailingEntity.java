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

import java.util.List;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;

@ImplementedBy(FailingEntityImpl.class)
public interface FailingEntity extends TestEntity {

    @SetFromFlag("failInSubTask")
    ConfigKey<Boolean> FAIL_IN_SUB_TASK = ConfigKeys.newBooleanConfigKey("failInSubTask", "Whether to throw exception in a sub-task (if true) or in current thread (if false)", false);
    
    @SetFromFlag("listener")
    ConfigKey<EventListener> LISTENER = ConfigKeys.newConfigKey(EventListener.class, "listener", "Whether to throw exception on call to start", EventListener.NOOP);
    
    @SetFromFlag("failOnStart")
    ConfigKey<Boolean> FAIL_ON_START = ConfigKeys.newBooleanConfigKey("failOnStart", "Whether to throw exception on call to start", false);
    
    @SetFromFlag("failOnStop")
    ConfigKey<Boolean> FAIL_ON_STOP = ConfigKeys.newBooleanConfigKey("failOnStop", "Whether to throw exception on call to stop", false);
    
    @SetFromFlag("failOnRestart")
    ConfigKey<Boolean> FAIL_ON_RESTART = ConfigKeys.newBooleanConfigKey("failOnRestart", "Whether to throw exception on call to restart", false);
    
    @SetFromFlag("failOnStartCondition")
    ConfigKey<Predicate<? super FailingEntity>> FAIL_ON_START_CONDITION = (ConfigKey) ConfigKeys.newConfigKey(Predicate.class, "failOnStartCondition", "Whether to throw exception on call to start", null);
    
    @SetFromFlag("failOnStopCondition")
    ConfigKey<Predicate<? super FailingEntity>> FAIL_ON_STOP_CONDITION = (ConfigKey) ConfigKeys.newConfigKey(Predicate.class, "failOnStopCondition", "Whether to throw exception on call to stop", null);
    
    @SetFromFlag("failOnRestartCondition")
    ConfigKey<Predicate<? super FailingEntity>> FAIL_ON_RESTART_CONDITION = (ConfigKey) ConfigKeys.newConfigKey(Predicate.class, "failOnRestartCondition", "Whether to throw exception on call to restart", null);
    
    @SetFromFlag("exceptionClazz")
    ConfigKey<Class<? extends RuntimeException>> EXCEPTION_CLAZZ = (ConfigKey) ConfigKeys.newConfigKey(Class.class, "exceptionClazz", "Type of exception to throw", IllegalStateException.class);
    
    @SetFromFlag("execOnFailure")
    ConfigKey<Function<? super FailingEntity,?>> EXEC_ON_FAILURE = (ConfigKey) ConfigKeys.newConfigKey(Function.class, "execOnFailure", "Callback to execute before throwing an exception, on any failure", Functions.identity());
    
    public interface EventListener {
        public static final EventListener NOOP = new EventListener() {
            @Override public void onEvent(Entity entity, String action, Object[] args) {}
        };
        
        public void onEvent(Entity entity, String action, Object[] args);
    }
    
    public static class RecordingEventListener implements EventListener {
        public final List<Object[]> events = Lists.newCopyOnWriteArrayList();
        
        @Override
        public void onEvent(Entity entity, String action, Object[] args) {
            events.add(new Object[] {entity, action, args});
        }
    }
}

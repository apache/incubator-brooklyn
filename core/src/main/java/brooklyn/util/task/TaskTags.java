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
package brooklyn.util.task;

import java.util.Set;

import javax.annotation.Nullable;

import brooklyn.management.Task;
import brooklyn.management.TaskAdaptable;

import com.google.common.base.Function;

public class TaskTags {

    /** marks a task which is allowed to fail without failing his parent */
    public static final String INESSENTIAL_TASK = "inessential";

    /** marks a task which is a subtask of another */
    public static final String SUB_TASK_TAG = "SUB-TASK";

    public static void addTagDynamically(TaskAdaptable<?> task, final Object tag) {
        ((BasicTask<?>)task.asTask()).applyTagModifier(new Function<Set<Object>, Void>() {
            public Void apply(@Nullable Set<Object> input) {
                input.add(tag);
                return null;
            }
        });
    }
    
    public static void addTagsDynamically(TaskAdaptable<?> task, final Object tag1, final Object ...tags) {
        ((BasicTask<?>)task.asTask()).applyTagModifier(new Function<Set<Object>, Void>() {
            public Void apply(@Nullable Set<Object> input) {
                input.add(tag1);
                for (Object tag: tags) input.add(tag);
                return null;
            }
        });
    }

    
    public static boolean isInessential(Task<?> task) {
        return task.getTags().contains(INESSENTIAL_TASK);
    }
    
    public static <U,V extends TaskAdaptable<U>> V markInessential(V task) {
        addTagDynamically(task, INESSENTIAL_TASK);
        return task;
    }

}

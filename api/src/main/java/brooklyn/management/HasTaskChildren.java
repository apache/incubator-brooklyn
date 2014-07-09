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
package brooklyn.management;

import com.google.common.annotations.Beta;

/** 
 * Interface marks tasks which have explicit children,
 * typically where the task defines the ordering of running those children tasks
 * <p>
 * The {@link Task#getSubmittedByTask()} on the child will typically return the parent,
 * but note there are other means of submitting tasks (e.g. background, in the same {@link ExecutionContext}),
 * where the submitter has no API reference to the submitted tasks.
 * <p>
 * In general the children mechanism is preferred as it is easier to navigate
 * (otherwise you have to scan the {@link ExecutionContext} to find tasks submitted by a task).  
 */
@Beta // in 0.6.0
public interface HasTaskChildren {

    public Iterable<Task<?>> getChildren();
    
}

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
package org.apache.brooklyn.rest.resources;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.brooklyn.api.mgmt.HasTaskChildren;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags.WrappedStream;
import org.apache.brooklyn.rest.api.ActivityApi;
import org.apache.brooklyn.rest.domain.TaskSummary;
import org.apache.brooklyn.rest.transform.TaskTransformer;
import org.apache.brooklyn.rest.util.WebResourceUtils;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;

public class ActivityResource extends AbstractBrooklynRestResource implements ActivityApi {

    @Override
    public TaskSummary get(String taskId) {
        Task<?> t = mgmt().getExecutionManager().getTask(taskId);
        if (t == null)
            throw WebResourceUtils.notFound("Cannot find task '%s'", taskId);
        return TaskTransformer.FROM_TASK.apply(t);
    }

    @Override
    public List<TaskSummary> children(String taskId) {
        Task<?> t = mgmt().getExecutionManager().getTask(taskId);
        if (t == null)
            throw WebResourceUtils.notFound("Cannot find task '%s'", taskId);
        if (!(t instanceof HasTaskChildren))
            return Collections.emptyList();
        return new LinkedList<TaskSummary>(Collections2.transform(Lists.newArrayList(((HasTaskChildren) t).getChildren()),
                TaskTransformer.FROM_TASK));
    }

    public String stream(String taskId, String streamId) {
        Task<?> t = mgmt().getExecutionManager().getTask(taskId);
        if (t == null)
            throw WebResourceUtils.notFound("Cannot find task '%s'", taskId);
        WrappedStream stream = BrooklynTaskTags.stream(t, streamId);
        if (stream == null)
            throw WebResourceUtils.notFound("Cannot find stream '%s' in task '%s'", streamId, taskId);
        return stream.streamContents.get();
    }
}

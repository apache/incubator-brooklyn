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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import brooklyn.management.Task;
import brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.collect.ForwardingObject;
import com.google.common.util.concurrent.ExecutionList;
import com.google.common.util.concurrent.ListenableFuture;

public abstract class ForwardingTask<T> extends ForwardingObject implements TaskInternal<T> {

    /** Constructor for use by subclasses. */
    protected ForwardingTask() {}

    @Override
    protected abstract TaskInternal<T> delegate();

    @Override
    public void addListener(Runnable listener, Executor executor) {
        delegate().addListener(listener, executor);
    }

    @Override
    public boolean cancel(boolean arg0) {
        return delegate().cancel(arg0);
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return delegate().get();
    }

    @Override
    public T get(long arg0, TimeUnit arg1) throws InterruptedException, ExecutionException, TimeoutException {
        return delegate().get(arg0, arg1);
    }

    @Override
    public boolean isCancelled() {
        return delegate().isCancelled();
    }

    @Override
    public boolean isDone() {
        return delegate().isDone();
    }

    @Override
    public Task<T> asTask() {
        return delegate().asTask();
    }

    @Override
    public String getId() {
        return delegate().getId();
    }

    @Override
    public Set<Object> getTags() {
        return delegate().getTags();
    }

    @Override
    public long getSubmitTimeUtc() {
        return delegate().getSubmitTimeUtc();
    }

    @Override
    public long getStartTimeUtc() {
        return delegate().getStartTimeUtc();
    }

    @Override
    public long getEndTimeUtc() {
        return delegate().getEndTimeUtc();
    }

    @Override
    public String getDisplayName() {
        return delegate().getDisplayName();
    }

    @Override
    public String getDescription() {
        return delegate().getDescription();
    }

    @Override
    public Task<?> getSubmittedByTask() {
        return delegate().getSubmittedByTask();
    }

    @Override
    public Thread getThread() {
        return delegate().getThread();
    }

    @Override
    public boolean isSubmitted() {
        return delegate().isSubmitted();
    }

    @Override
    public boolean isBegun() {
        return delegate().isBegun();
    }

    @Override
    public boolean isError() {
        return delegate().isError();
    }

    @Override
    public void blockUntilStarted() {
        delegate().blockUntilStarted();
    }

    @Override
    public void blockUntilEnded() {
        delegate().blockUntilEnded();
    }

    @Override
    public boolean blockUntilEnded(Duration timeout) {
        return delegate().blockUntilEnded(timeout);
    }

    @Override
    public String getStatusSummary() {
        return delegate().getStatusSummary();
    }

    @Override
    public String getStatusDetail(boolean multiline) {
        return delegate().getStatusDetail(multiline);
    }

    @Override
    public T get(Duration duration) throws InterruptedException, ExecutionException, TimeoutException {
        return delegate().get(duration);
    }

    @Override
    public T getUnchecked() {
        return delegate().getUnchecked();
    }

    @Override
    public T getUnchecked(Duration duration) {
        return delegate().getUnchecked(duration);
    }

    @Override
    public void initResult(ListenableFuture<T> result) {
        delegate().initResult(result);
    }

    @Override
    public long getQueuedTimeUtc() {
        return delegate().getQueuedTimeUtc();
    }

    @Override
    public Future<T> getResult() {
        return delegate().getResult();
    }

    @Override
    public boolean isQueued() {
        return delegate().isQueued();
    }

    @Override
    public boolean isQueuedOrSubmitted() {
        return delegate().isQueuedOrSubmitted();
    }

    @Override
    public boolean isQueuedAndNotSubmitted() {
        return delegate().isQueuedAndNotSubmitted();
    }

    @Override
    public void markQueued() {
        delegate().markQueued();
    }

    @Override
    public boolean cancel() {
        return delegate().cancel();
    }

    @Override
    public boolean blockUntilStarted(Duration timeout) {
        return delegate().blockUntilStarted(timeout);
    }

    @Override
    public String setBlockingDetails(String blockingDetails) {
        return delegate().setBlockingDetails(blockingDetails);
    }

    @Override
    public Task<?> setBlockingTask(Task<?> blockingTask) {
        return delegate().setBlockingTask(blockingTask);
    }

    @Override
    public void resetBlockingDetails() {
        delegate().resetBlockingDetails();
    }

    @Override
    public void resetBlockingTask() {
        delegate().resetBlockingTask();
    }

    @Override
    public String getBlockingDetails() {
        return delegate().getBlockingDetails();
    }

    @Override
    public Task<?> getBlockingTask() {
        return delegate().getBlockingTask();
    }

    @Override
    public void setExtraStatusText(Object extraStatus) {
        delegate().setExtraStatusText(extraStatus);
    }

    @Override
    public Object getExtraStatusText() {
        return delegate().getExtraStatusText();
    }

    @Override
    public void runListeners() {
        delegate().runListeners();
    }

    @Override
    public void setEndTimeUtc(long val) {
        delegate().setEndTimeUtc(val);
    }

    @Override
    public void setThread(Thread thread) {
        delegate().setThread(thread);
    }

    @Override
    public Callable<T> getJob() {
        return delegate().getJob();
    }

    @Override
    public void setJob(Callable<T> job) {
        delegate().setJob(job);
    }

    @Override
    public ExecutionList getListeners() {
        return delegate().getListeners();
    }

    @Override
    public void setSubmitTimeUtc(long currentTimeMillis) {
        delegate().setSubmitTimeUtc(currentTimeMillis);
    }

    @Override
    public void setSubmittedByTask(Task<?> task) {
        delegate().setSubmittedByTask(task);
    }

    @Override
    public Set<Object> getMutableTags() {
        return delegate().getMutableTags();
    }

    @Override
    public void setStartTimeUtc(long currentTimeMillis) {
        delegate().setStartTimeUtc(currentTimeMillis);
    }

    @Override
    public void applyTagModifier(Function<Set<Object>, Void> modifier) {
        delegate().applyTagModifier(modifier);
    }
}

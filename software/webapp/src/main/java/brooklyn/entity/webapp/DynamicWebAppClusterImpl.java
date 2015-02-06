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
package brooklyn.entity.webapp;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.event.AttributeSensor;
import brooklyn.management.Task;
import brooklyn.management.TaskAdaptable;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.TaskBuilder;
import brooklyn.util.task.TaskTags;
import brooklyn.util.task.Tasks;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * DynamicWebAppClusters provide cluster-wide aggregates of entity attributes.  Currently totals and averages:
 * <ul>
 *   <li>Entity request counts</li>
 *   <li>Entity error counts</li>
 *   <li>Requests per second</li>
 *   <li>Entity processing time</li>
 * </ul>
 */
public class DynamicWebAppClusterImpl extends DynamicClusterImpl implements DynamicWebAppCluster {

    private static final Logger log = LoggerFactory.getLogger(DynamicWebAppClusterImpl.class);
    private static final FilenameToWebContextMapper FILENAME_TO_WEB_CONTEXT_MAPPER = new FilenameToWebContextMapper();
    
    /**
     * Instantiate a new DynamicWebAppCluster.  Parameters as per {@link DynamicCluster#DynamicCluster()}
     */
    public DynamicWebAppClusterImpl() {
        super();
    }
    
    @Override
    public void init() {
        super.init();
        // Enricher attribute setup.  A way of automatically discovering these (but avoiding
        // averaging things like HTTP port and response codes) would be neat.
        List<? extends List<? extends AttributeSensor<? extends Number>>> summingEnricherSetup = ImmutableList.of(
                ImmutableList.of(REQUEST_COUNT, REQUEST_COUNT),
                ImmutableList.of(ERROR_COUNT, ERROR_COUNT),
                ImmutableList.of(REQUESTS_PER_SECOND_LAST, REQUESTS_PER_SECOND_LAST),
                ImmutableList.of(REQUESTS_PER_SECOND_IN_WINDOW, REQUESTS_PER_SECOND_IN_WINDOW),
                ImmutableList.of(TOTAL_PROCESSING_TIME, TOTAL_PROCESSING_TIME),
                ImmutableList.of(PROCESSING_TIME_FRACTION_IN_WINDOW, PROCESSING_TIME_FRACTION_IN_WINDOW)
        );
        
        List<? extends List<? extends AttributeSensor<? extends Number>>> averagingEnricherSetup = ImmutableList.of(
                ImmutableList.of(REQUEST_COUNT, REQUEST_COUNT_PER_NODE),
                ImmutableList.of(ERROR_COUNT, ERROR_COUNT_PER_NODE),
                ImmutableList.of(REQUESTS_PER_SECOND_LAST, REQUESTS_PER_SECOND_LAST_PER_NODE),
                ImmutableList.of(REQUESTS_PER_SECOND_IN_WINDOW, REQUESTS_PER_SECOND_IN_WINDOW_PER_NODE),
                ImmutableList.of(TOTAL_PROCESSING_TIME, TOTAL_PROCESSING_TIME_PER_NODE),
                ImmutableList.of(PROCESSING_TIME_FRACTION_IN_WINDOW, PROCESSING_TIME_FRACTION_IN_WINDOW_PER_NODE)
        );
        
        for (List<? extends AttributeSensor<? extends Number>> es : summingEnricherSetup) {
            AttributeSensor<? extends Number> t = es.get(0);
            AttributeSensor<? extends Number> total = es.get(1);
            addEnricher(Enrichers.builder()
                    .aggregating(t)
                    .publishing(total)
                    .fromMembers()
                    .computingSum()
                    .build());
        }
        
        for (List<? extends AttributeSensor<? extends Number>> es : averagingEnricherSetup) {
            @SuppressWarnings("unchecked")
            AttributeSensor<Number> t = (AttributeSensor<Number>) es.get(0);
            @SuppressWarnings("unchecked")
            AttributeSensor<Double> average = (AttributeSensor<Double>) es.get(1);
            addEnricher(Enrichers.builder()
                    .aggregating(t)
                    .publishing(average)
                    .fromMembers()
                    .computingAverage()
                    .build());
        }
    }
    
    // TODO this will probably be useful elsewhere ... but where to put it?
    // TODO add support for this in DependentConfiguration (see TODO there)
    /** Waits for the given target to report service up, then runs the given task
     * (often an invocation on that entity), with the given name.
     * If the target goes away, this task marks itself inessential
     * before failing so as not to cause a parent task to fail. */
    static <T> Task<T> whenServiceUp(final Entity target, final TaskAdaptable<T> task, String name) {
        return Tasks.<T>builder().name(name).dynamic(true).body(new Callable<T>() {
            @Override
            public T call() {
                try {
                    while (true) {
                        if (!Entities.isManaged(target)) {
                            Tasks.markInessential();
                            throw new IllegalStateException("Target "+target+" is no longer managed");
                        }
                        if (Boolean.TRUE.equals(target.getAttribute(Attributes.SERVICE_UP))) {
                            Tasks.resetBlockingDetails();
                            TaskTags.markInessential(task);
                            DynamicTasks.queue(task);
                            try {
                                return task.asTask().getUnchecked();
                            } catch (Exception e) {
                                if (Entities.isManaged(target)) {
                                    throw Exceptions.propagate(e);
                                } else {
                                    Tasks.markInessential();
                                    throw new IllegalStateException("Target "+target+" is no longer managed", e);
                                }
                            }
                        } else {
                            Tasks.setBlockingDetails("Waiting on "+target+" to be ready");
                        }
                        // TODO replace with subscription?
                        Time.sleep(Duration.ONE_SECOND);
                    }
                } finally {
                    Tasks.resetBlockingDetails();
                }
            }
        }).build();        
    }

    @Override
    public void deploy(String url, String targetName) {
        checkNotNull(url, "url");
        checkNotNull(targetName, "targetName");
        targetName = FILENAME_TO_WEB_CONTEXT_MAPPER.convertDeploymentTargetNameToContext(targetName);

        // set it up so future nodes get the right wars
        addToWarsByContext(this, url, targetName);
        
        log.debug("Deploying "+targetName+"->"+url+" across cluster "+this+"; WARs now "+getConfig(WARS_BY_CONTEXT));

        Iterable<CanDeployAndUndeploy> targets = Iterables.filter(getChildren(), CanDeployAndUndeploy.class);
        TaskBuilder<Void> tb = Tasks.<Void>builder().parallel(true).name("Deploy "+targetName+" to cluster (size "+Iterables.size(targets)+")");
        for (Entity target: targets) {
            tb.add(whenServiceUp(target, Effectors.invocation(target, DEPLOY, MutableMap.of("url", url, "targetName", targetName)),
                "Deploy "+targetName+" to "+target+" when ready"));
        }
        DynamicTasks.queueIfPossible(tb.build()).orSubmitAsync(this).asTask().getUnchecked();

        // Update attribute
        // TODO support for atomic sensor update (should be part of standard tooling; NB there is some work towards this, according to @aledsage)
        Set<String> deployedWars = MutableSet.copyOf(getAttribute(DEPLOYED_WARS));
        deployedWars.add(targetName);
        setAttribute(DEPLOYED_WARS, deployedWars);
    }
    
    @Override
    public void undeploy(String targetName) {
        checkNotNull(targetName, "targetName");
        targetName = FILENAME_TO_WEB_CONTEXT_MAPPER.convertDeploymentTargetNameToContext(targetName);
        
        // set it up so future nodes get the right wars
        if (!removeFromWarsByContext(this, targetName)) {
            DynamicTasks.submit(Tasks.warning("Context "+targetName+" not known at "+this+"; attempting to undeploy regardless", null), this);
        }
        
        log.debug("Undeploying "+targetName+" across cluster "+this+"; WARs now "+getConfig(WARS_BY_CONTEXT));

        Iterable<CanDeployAndUndeploy> targets = Iterables.filter(getChildren(), CanDeployAndUndeploy.class);
        TaskBuilder<Void> tb = Tasks.<Void>builder().parallel(true).name("Undeploy "+targetName+" across cluster (size "+Iterables.size(targets)+")");
        for (Entity target: targets) {
            tb.add(whenServiceUp(target, Effectors.invocation(target, UNDEPLOY, MutableMap.of("targetName", targetName)),
                "Undeploy "+targetName+" at "+target+" when ready"));
        }
        DynamicTasks.queueIfPossible(tb.build()).orSubmitAsync(this).asTask().getUnchecked();

        // Update attribute
        Set<String> deployedWars = MutableSet.copyOf(getAttribute(DEPLOYED_WARS));
        deployedWars.remove( FILENAME_TO_WEB_CONTEXT_MAPPER.convertDeploymentTargetNameToContext(targetName) );
        setAttribute(DEPLOYED_WARS, deployedWars);
    }

    static void addToWarsByContext(Entity entity, String url, String targetName) {
        targetName = FILENAME_TO_WEB_CONTEXT_MAPPER.convertDeploymentTargetNameToContext(targetName);
        // TODO a better way to do atomic updates, see comment above
        synchronized (entity) {
            Map<String,String> newWarsMap = MutableMap.copyOf(entity.getConfig(WARS_BY_CONTEXT));
            newWarsMap.put(targetName, url);
            ((EntityInternal)entity).setConfig(WARS_BY_CONTEXT, newWarsMap);
        }
    }

    static boolean removeFromWarsByContext(Entity entity, String targetName) {
        targetName = FILENAME_TO_WEB_CONTEXT_MAPPER.convertDeploymentTargetNameToContext(targetName);
        // TODO a better way to do atomic updates, see comment above
        synchronized (entity) {
            Map<String,String> newWarsMap = MutableMap.copyOf(entity.getConfig(WARS_BY_CONTEXT));
            String url = newWarsMap.remove(targetName);
            if (url==null) {
                return false;
            }
            ((EntityInternal)entity).setConfig(WARS_BY_CONTEXT, newWarsMap);
            return true;
        }
    }
    
    @Override
    public void redeployAll() {
        Map<String, String> wars = MutableMap.copyOf(getConfig(WARS_BY_CONTEXT));
        String redeployPrefix = "Redeploy all WARs (count "+wars.size()+")";

        log.debug("Redeplying all WARs across cluster "+this+": "+getConfig(WARS_BY_CONTEXT));
        
        Iterable<CanDeployAndUndeploy> targetEntities = Iterables.filter(getChildren(), CanDeployAndUndeploy.class);
        TaskBuilder<Void> tb = Tasks.<Void>builder().parallel(true).name(redeployPrefix+" across cluster (size "+Iterables.size(targetEntities)+")");
        for (Entity targetEntity: targetEntities) {
            TaskBuilder<Void> redeployAllToTarget = Tasks.<Void>builder().name(redeployPrefix+" at "+targetEntity+" (after ready check)");
            for (String warContextPath: wars.keySet()) {
                redeployAllToTarget.add(Effectors.invocation(targetEntity, DEPLOY, MutableMap.of("url", wars.get(warContextPath), "targetName", warContextPath)));
            }
            tb.add(whenServiceUp(targetEntity, redeployAllToTarget.build(), redeployPrefix+" at "+targetEntity+" when ready"));
        }
        DynamicTasks.queueIfPossible(tb.build()).orSubmitAsync(this).asTask().getUnchecked();
    }  

}

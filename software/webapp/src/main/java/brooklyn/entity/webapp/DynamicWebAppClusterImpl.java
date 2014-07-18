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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Predicates;
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
    private FilenameToWebContextMapper filenameToWebContextMapper = new FilenameToWebContextMapper();
    
    /**
     * Instantiate a new DynamicWebAppCluster.  Parameters as per {@link DynamicCluster#DynamicCluster()}
     */
    public DynamicWebAppClusterImpl() {
        super();
    }
    
    @Override
    public void onManagementBecomingMaster() {
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

        subscribeToMembers(this, SERVICE_UP, new SensorEventListener<Boolean>() {
            @Override public void onEvent(SensorEvent<Boolean> event) {
                if (!isRebinding()) {
                    setAttribute(SERVICE_UP, calculateServiceUp());
                }
            }
        });
    }

    @Override
    public synchronized boolean addMember(Entity member) {
        boolean result = super.addMember(member);
        if (!isRebinding()) {
            setAttribute(SERVICE_UP, calculateServiceUp());
        }
        return result;
    }
    
    @Override
    public synchronized boolean removeMember(Entity member) {
        boolean result = super.removeMember(member);
        if (!isRebinding()) {
            setAttribute(SERVICE_UP, calculateServiceUp());
        }
        return result;
    }

    @Override    
    protected boolean calculateServiceUp() {
        boolean up = false;
        for (Entity member : getMembers()) {
            if (Boolean.TRUE.equals(member.getAttribute(SERVICE_UP))) up = true;
        }
        return up;
    }
    
    /**
     * Deploys the given artifact, from a source URL, to all members of the cluster
     * See {@link FileNameToContextMappingTest} for definitive examples!
     * 
     * @param url  where to get the war, as a URL, either classpath://xxx or file:///home/xxx or http(s)...
     * @param targetName  where to tell the server to serve the WAR, see above
     */
    @Effector(description="Deploys the given artifact, from a source URL, to a given deployment filename/context")
    public void deploy(
            @EffectorParam(name="url", description="URL of WAR file") String url, 
            @EffectorParam(name="targetName", description="context path where WAR should be deployed (/ for ROOT)") String targetName) {
        try {
            checkNotNull(url, "url");
            checkNotNull(targetName, "targetName");
            
            // set it up so future nodes get the right wars
            synchronized (this) {
                Map<String,String> newWarsMap = MutableMap.copyOf(getConfig(WARS_BY_CONTEXT));
                newWarsMap.put(targetName, url);
                setConfig(WARS_BY_CONTEXT, newWarsMap);
            }
            
            // now actually deploy
            List <Entity> clusterMembers = MutableList.copyOf(
                Iterables.filter(getChildren(), Predicates.and(
                     Predicates.instanceOf(JavaWebAppSoftwareProcess.class),
                     EntityPredicates.attributeEqualTo(SERVICE_STATE, Lifecycle.RUNNING)
            )));
            Entities.invokeEffectorListWithArgs(this, clusterMembers, DEPLOY, url, targetName).get();            
            
            // Update attribute
            Set<String> deployedWars = MutableSet.copyOf(getAttribute(DEPLOYED_WARS));
            deployedWars.add(targetName);
            setAttribute(DEPLOYED_WARS, deployedWars);
            
        } catch (Exception e) {
            // Log and propagate, so that log says which entity had problems...
            log.warn("Error deploying '"+url+"' to "+targetName+" on "+toString()+"; rethrowing...", e);
            throw Exceptions.propagate(e);
        }
    }
    
    /*
     * TODO
     * 
     * - deploy to all, not just running, with a wait-for-running-or-bail-out logic
     * - thread pool
     * - redeploy to all (simple way, with notes)
     */
    
    /** For the DEPLOYED_WARS to be updated, the input must match the result of the call to deploy */
    @Effector(description="Undeploys the given context/artifact")
    public void undeploy(@EffectorParam(name="targetName") String targetName) {
        
        try {
            checkNotNull(targetName, "targetName");
            
            // set it up so future nodes get the right wars
            synchronized (this) {
                Map<String,String> newWarsMap = MutableMap.copyOf(getConfig(WARS_BY_CONTEXT));
                newWarsMap.remove(targetName);
                setConfig(WARS_BY_CONTEXT, newWarsMap);
            }

            List <Entity> clusterMembers = MutableList.copyOf(
                Iterables.filter(getChildren(), Predicates.and(
                     Predicates.instanceOf(JavaWebAppSoftwareProcess.class),
                     EntityPredicates.attributeEqualTo(SERVICE_STATE, Lifecycle.RUNNING)
            )));
            Entities.invokeEffectorListWithArgs(this, clusterMembers, UNDEPLOY, targetName).get(); 
            
            // Update attribute
            Set<String> deployedWars = MutableSet.copyOf(getAttribute(DEPLOYED_WARS));
            deployedWars.remove( filenameToWebContextMapper.convertDeploymentTargetNameToContext(targetName) );
            setAttribute(DEPLOYED_WARS, deployedWars);
            
        } catch (Exception e) {
            // Log and propagate, so that log says which entity had problems...
            log.warn("Error undeploying '"+targetName+"' on "+toString()+"; rethrowing...", e);
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public void redeployAll() {
        throw new UnsupportedOperationException("TODO - support redeploying all WARs (if any of the deploy/undeploys fail)");
    }  

}

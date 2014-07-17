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
package brooklyn.rest.resources;

import static brooklyn.rest.util.WebResourceUtils.notFound;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Lifecycle;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.management.usage.ApplicationUsage;
import brooklyn.management.usage.ApplicationUsage.ApplicationEvent;
import brooklyn.management.usage.LocationUsage;
import brooklyn.rest.api.UsageApi;
import brooklyn.rest.domain.UsageStatistic;
import brooklyn.rest.domain.UsageStatistics;
import brooklyn.rest.transform.ApplicationTransformer;
import brooklyn.util.exceptions.UserFacingException;
import brooklyn.util.time.Time;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;


public class UsageResource extends AbstractBrooklynRestResource implements UsageApi {

    private static final Logger log = LoggerFactory.getLogger(UsageResource.class);

    private static final Set<Lifecycle> WORKING_LIFECYCLES = ImmutableSet.of(Lifecycle.RUNNING, Lifecycle.CREATED, Lifecycle.STARTING);

    // SimpleDateFormat is not thread-safe, so give one to each thread
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMATTER = new ThreadLocal<SimpleDateFormat>(){
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat(DATE_FORMAT);
        }
    };
    
    @Override
    public List<UsageStatistics> listApplicationsUsage(@Nullable String start, @Nullable String end) {
        log.debug("REST call to get application usage for all applications: dates {} -> {}", new Object[] {start, end});
        
        List<UsageStatistics> response = Lists.newArrayList();
        
        Date startDate = parseDate(start, new Date(0));
        Date endDate = parseDate(end, new Date());
        
        checkDates(startDate, endDate);

        Set<ApplicationUsage> usages = ((ManagementContextInternal) mgmt()).getUsageManager().getApplicationUsage(Predicates.alwaysTrue());

        for (ApplicationUsage usage : usages) {
            List<UsageStatistic> statistics = retrieveApplicationUsage(usage, startDate, endDate);
            if (statistics.size() > 0) {
                response.add(new UsageStatistics(statistics, ImmutableMap.<String,URI>of()));
            }
        }
        return response;
    }
    
    @Override
    public UsageStatistics getApplicationUsage(String application, String start, String end) {
        log.debug("REST call to get application usage for application {}: dates {} -> {}", new Object[] {application, start, end});
        
        Date startDate = parseDate(start, new Date(0));
        Date endDate = parseDate(end, new Date());

        checkDates(startDate, endDate);

        ApplicationUsage usage = ((ManagementContextInternal) mgmt()).getUsageManager().getApplicationUsage(application);
        if (usage != null) {
            List<UsageStatistic> statistics = retrieveApplicationUsage(usage, startDate, endDate);
            return new UsageStatistics(statistics, ImmutableMap.<String,URI>of());
        } else {
            throw notFound("Application '%s' not found", application);
        }
    }

    private List<UsageStatistic> retrieveApplicationUsage(ApplicationUsage usage, Date startDate, Date endDate) {
        log.debug("Determining application usage for application {}: dates {} -> {}", new Object[] {usage.getApplicationId(), startDate, endDate});
        log.trace("Considering application usage events of {}: {}", usage.getApplicationId(), usage.getEvents());

        List<UsageStatistic> result = Lists.newArrayList();

        // Getting duration of state by comparing with next event (if next event is of same type, we just generate two statistics)...
        for (int i = 0; i < usage.getEvents().size(); i++) {
            ApplicationEvent current = usage.getEvents().get(i);
            Date eventStartDate = current.getDate();
            Date eventEndDate;

            if (i <  usage.getEvents().size() - 1) {
                ApplicationEvent next =  usage.getEvents().get(i + 1);
                eventEndDate = next.getDate();
            } else if (current.getState() == Lifecycle.DESTROYED) {
                eventEndDate = eventStartDate;
            } else {
                eventEndDate = new Date();
            }

            if (eventStartDate.compareTo(endDate) > 0 || eventEndDate.compareTo(startDate) < 0) {
                continue;
            }

            if (eventStartDate.compareTo(startDate) < 0) {
                eventStartDate = startDate;
            }
            if (eventEndDate.compareTo(endDate) > 0) {
                eventEndDate = endDate;
            }
            long duration = eventEndDate.getTime() - eventStartDate.getTime();
            UsageStatistic statistic = new UsageStatistic(ApplicationTransformer.statusFromLifecycle(current.getState()), usage.getApplicationId(), usage.getApplicationId(), format(eventStartDate), format(eventEndDate), duration, usage.getMetadata());
            log.trace("Adding application usage statistic to response for app {}: {}", usage.getApplicationId(), statistic);
            result.add(statistic);
        }
        
        return result;
    }

    @Override
    public List<UsageStatistics> listMachinesUsage(final String application, final String start, final String end) {
        log.debug("REST call to get machine usage for application {}: dates {} -> {}", new Object[] {application, start, end});
        
        final Date startDate = parseDate(start, new Date(0));
        final Date endDate = parseDate(end, new Date());

        checkDates(startDate, endDate);
        
        // Note currently recording ALL metrics for a machine that contains an Event from given Application
        Set<LocationUsage> matches = ((ManagementContextInternal) mgmt()).getUsageManager().getLocationUsage(new Predicate<LocationUsage>() {
            @Override
            public boolean apply(LocationUsage input) {
                LocationUsage.LocationEvent first = input.getEvents().get(0);
                if (endDate.compareTo(first.getDate()) < 0) {
                    return false;
                }
                LocationUsage.LocationEvent last = input.getEvents().get(input.getEvents().size() - 1);
                if (!WORKING_LIFECYCLES.contains(last.getState()) && startDate.compareTo(last.getDate()) > 0) {
                    return false;
                }
                if (application != null) {
                    for (LocationUsage.LocationEvent e : input.getEvents()) {
                        if (Objects.equal(application, e.getApplicationId())) {
                            return true;
                        }
                    }
                    return false;
                }
                return true;
            }
        });
        
        List<UsageStatistics> response = Lists.newArrayList();
        for (LocationUsage usage : matches) {
            List<UsageStatistic> statistics = retrieveMachineUsage(usage, startDate, endDate);
            if (statistics.size() > 0) {
                response.add(new UsageStatistics(statistics, ImmutableMap.<String,URI>of()));
            }
        }
        return response;
    }

    @Override
    public UsageStatistics getMachineUsage(final String machine, final String start, final String end) {
        log.debug("REST call to get machine usage for machine {}: dates {} -> {}", new Object[] {machine, start, end});
        
        final Date startDate = parseDate(start, new Date(0));
        final Date endDate = parseDate(end, new Date());

        checkDates(startDate, endDate);
        
        // Note currently recording ALL metrics for a machine that contains an Event from given Application
        LocationUsage usage = ((ManagementContextInternal) mgmt()).getUsageManager().getLocationUsage(machine);
        
        if (usage == null) {
            throw notFound("Machine '%s' not found", machine);
        }
        
        List<UsageStatistic> statistics = retrieveMachineUsage(usage, startDate, endDate);
        return new UsageStatistics(statistics, ImmutableMap.<String,URI>of());
    }

    private List<UsageStatistic> retrieveMachineUsage(LocationUsage usage, Date startDate, Date endDate) {
        log.debug("Determining machine usage for location {}", usage.getLocationId());
        log.trace("Considering machine usage events of {}: {}", usage.getLocationId(), usage.getEvents());

        List<UsageStatistic> result = Lists.newArrayList();

        // Getting duration of state by comparing with next event (if next event is of same type, we just generate two statistics)...
        for (int i = 0; i < usage.getEvents().size(); i++) {
            LocationUsage.LocationEvent current = usage.getEvents().get(i);
            Date eventStartDate = current.getDate();
            Date eventEndDate;

            if (i <  usage.getEvents().size() - 1) {
                LocationUsage.LocationEvent next =  usage.getEvents().get(i + 1);
                eventEndDate = next.getDate();
            } else if (current.getState() == Lifecycle.DESTROYED || current.getState() == Lifecycle.STOPPED) {
                eventEndDate = eventStartDate;
            } else {
                eventEndDate = new Date();
            }

            if (eventStartDate.compareTo(endDate) > 0 || eventEndDate.compareTo(startDate) < 0) {
                continue;
            }

            if (eventStartDate.compareTo(startDate) < 0) {
                eventStartDate = startDate;
            }
            if (eventEndDate.compareTo(endDate) > 0) {
                eventEndDate = endDate;
            }
            long duration = eventEndDate.getTime() - eventStartDate.getTime();
            UsageStatistic statistic = new UsageStatistic(ApplicationTransformer.statusFromLifecycle(current.getState()), usage.getLocationId(), current.getApplicationId(), format(eventStartDate), format(eventEndDate), duration, usage.getMetadata());
            log.trace("Adding machine usage statistic to response for app {}: {}", usage.getLocationId(), statistic);
            result.add(statistic);
        }
        
        return result;
    }
    
    private void checkDates(Date startDate, Date endDate) {
        if (startDate.compareTo(endDate) > 0) {
            throw new UserFacingException(new IllegalArgumentException("Start must be less than or equal to end: " + startDate + " > " + endDate + 
                    " (" + startDate.getTime() + " > " + endDate.getTime() + ")"));
        }
    }

    private Date parseDate(String toParse, Date def) {
        return (toParse == null) ? def : Time.parseDateString(toParse, DATE_FORMATTER.get());
    }
    
    private String format(Date date) {
        return DATE_FORMATTER.get().format(date);
    }
}

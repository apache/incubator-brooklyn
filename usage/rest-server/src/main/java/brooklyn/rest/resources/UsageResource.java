package brooklyn.rest.resources;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.ApplicationUsage;
import brooklyn.entity.basic.ApplicationUsage.ApplicationEvent;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.location.basic.LocationUsage;
import brooklyn.management.internal.LocalLocationManager;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.rest.api.UsageApi;
import brooklyn.rest.domain.Statistic;
import brooklyn.rest.transform.ApplicationTransformer;
import brooklyn.rest.util.JsonUtils;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class UsageResource extends AbstractBrooklynRestResource implements UsageApi {

    private static final Logger log = LoggerFactory.getLogger(UsageResource.class);

    private static final Set<Lifecycle> WORKING_LIFECYCLES = ImmutableSet.of(Lifecycle.RUNNING, Lifecycle.CREATED, Lifecycle.STARTING);

    // SimpleDateFormat is not thread-safe, so give one to each thread
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMATTER = new ThreadLocal<SimpleDateFormat>(){
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat(JsonUtils.DATE_FORMAT);
        }
    };
    
    @Override
    public List<Statistic> listApplicationUsage(@Nullable String start, @Nullable String end) {
        log.debug("REST call to get application usage for all applications: dates {} -> {}", new Object[] {start, end});
        
        List<Statistic> response = Lists.newArrayList();
        
        Date startDate = parseSafely(start, new Date(0));
        Date endDate = parseSafely(end, new Date());
        Map<String, ApplicationUsage> usages = getApplicationUsage();
        
        checkDates(startDate, endDate);

        for (ApplicationUsage usage : usages.values()) {
            retrieveApplicationUsage(usage, startDate, endDate, response);
        }
        return response;
    }
    
    @Override
    public List<Statistic> getApplicationUsage(String application, String start, String end) {
        log.debug("REST call to get application usage for application {}: dates {} -> {}", new Object[] {application, start, end});
        
        Date startDate = parseSafely(start, new Date(0));
        Date endDate = parseSafely(end, new Date());

        checkDates(startDate, endDate);
        
        List<Statistic> response = Lists.newArrayList();
        retrieveApplicationUsage(application,  startDate, endDate, response);
        return response;
    }

    private void retrieveApplicationUsage(String application, Date startDate, Date endDate, List<Statistic> list) {
        ApplicationUsage usage = getApplicationUsage().get(application);
        if (usage != null) {
            retrieveApplicationUsage(usage, startDate, endDate, list);
        }
    }
    
    private void retrieveApplicationUsage(ApplicationUsage usage, Date startDate, Date endDate, List<Statistic> list) {
        log.debug("Determining application usage for application {}: dates {} -> {}", new Object[] {usage.getApplicationId(), startDate, endDate});
        log.trace("Considering application usage events of {}: {}", usage.getApplicationId(), usage.getEvents());

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
            Statistic statistic = new Statistic(ApplicationTransformer.statusFromLifecycle(current.getState()), usage.getApplicationId(), usage.getApplicationId(), format(eventStartDate), format(eventEndDate), duration, usage.getMetadata());
            log.trace("Adding application usage statistic to response for app {}: {}", usage.getApplicationId(), statistic);
            list.add(statistic);
        }
    }

    @Override
    public List<Statistic> listMachineUsages(final String application, final String start, final String end) {
        log.debug("REST call to get machine usage for application {}: dates {} -> {}", new Object[] {application, start, end});
        
        final Date startDate = parseSafely(start, new Date(0));
        final Date endDate = parseSafely(end, new Date());

        checkDates(startDate, endDate);
        
        // Note currently recording ALL metrics for a machine that contains an Event from given Application
        Set<LocationUsage> matches = ((LocalLocationManager) mgmt().getLocationManager()).getLocationUsage(new Predicate<LocationUsage>() {
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
        
        List<Statistic> results = Lists.newArrayList();
        for (LocationUsage usage : matches) {
            retrieveMachineUsage(usage, startDate, endDate, results);
        }
        return results;
    }

    private void retrieveMachineUsage(LocationUsage usage, Date startDate, Date endDate, List<Statistic> list) {
        log.debug("Determining machine usage for location {}", usage.getLocationId());
        log.trace("Considering machine usage events of {}: {}", usage.getLocationId(), usage.getEvents());

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
            Statistic statistic = new Statistic(ApplicationTransformer.statusFromLifecycle(current.getState()), usage.getLocationId(), current.getApplicationId(), format(eventStartDate), format(eventEndDate), duration, usage.getMetadata());
            log.trace("Adding machine usage statistic to response for app {}: {}", usage.getLocationId(), statistic);
            list.add(statistic);
        }
    }
    
    private Map<String, ApplicationUsage> getApplicationUsage() {
        return ((ManagementContextInternal) mgmt()).getStorage().<String, ApplicationUsage>getMap(AbstractApplication.APPLICATION_USAGE_KEY);
    }
    
    private void checkDates(Date startDate, Date endDate) {
        if (startDate.compareTo(endDate) > 0) {
            throw new IllegalArgumentException("Start must be less than or equal to end: " + startDate + " > " + endDate + 
                    " (" + startDate.getTime() + " > " + endDate.getTime() + ")");
        }
    }

    private Date parseSafely(String toParse, Date def) {       
        if (toParse == null) {
            return def;
        }
        if (!toParse.contains("-")) {
            // doesn't look like date-format; try UTC millis number
            try {
                return new Date(Long.parseLong(toParse.trim()));
            } catch (NumberFormatException e) {
                // continue; try as date-format
            }
        }
        try {
            // fix the usual + => ' ' nonsense when passing dates as query params
            toParse = toParse.replace(" ", "+");
            return DATE_FORMATTER.get().parse(toParse);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Date " + toParse + " cannot be parsed as UTC millis or using format " + JsonUtils.DATE_FORMAT);
        }
    }
    
    private String format(Date date) {
        return DATE_FORMATTER.get().format(date);
    }
}

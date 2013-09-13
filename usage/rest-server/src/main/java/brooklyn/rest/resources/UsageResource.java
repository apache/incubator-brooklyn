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
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;


public class UsageResource extends AbstractBrooklynRestResource implements UsageApi {

    private static final Logger log = LoggerFactory.getLogger(UsageResource.class);
    private static final String dateFormatPattern = "yyyy-MM-dd'T'HH:mm:ssZ";
    
    @Override
    public List<Statistic> listApplicationUsage(@Nullable String startDate, @Nullable String stopDate) {
        List<Statistic> response = Lists.newArrayList();
        Map<String, List<ApplicationEvent>> events = ((ManagementContextInternal) mgmt()).getStorage().<String, List<ApplicationEvent>>getMap(AbstractApplication.APPLICATION_USAGE_KEY);
        
        for (String app : events.keySet()) {
            retrieveApplicationUsage(app, startDate,  stopDate, response);
        }
        return response;
    }
        
    private Date parseSafely(DateFormat dateFormat, String toParse, Date def) {
        if (toParse == null) {
            return def;
        }
        try {
            // fix the usual + => ' ' nonsense when passing dates as query params
            toParse = toParse.replace(" ", "+");
            return dateFormat.parse(toParse);
        } catch (ParseException e) {
            log.error("grrrr", e);
            return def;
        }
    }

    @Override
    public List<Statistic> getApplicationUsage(String application, @Nullable String startDate, @Nullable String stopDate) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(dateFormatPattern);
        List<Statistic> response = Lists.newArrayList();
        retrieveApplicationUsage(application,  startDate, stopDate, response);
        return response;
    }

    private void retrieveApplicationUsage(String application, String startMarker, String stopMarker, List<Statistic> list) {
        log.warn("Determining usage from events for application " + application + " start " + startMarker + " stop " + stopMarker);
        SimpleDateFormat dateFormat = new SimpleDateFormat(dateFormatPattern);

        final Date startDate = parseSafely(dateFormat, startMarker, new Date(0));
        final Date stopDate = parseSafely(dateFormat, stopMarker, new Date());

        if (startDate.compareTo(stopDate) > 0) {
            return;
        }

        ApplicationUsage usage = ((ManagementContextInternal) mgmt()).getStorage().<String, ApplicationUsage>getMap(AbstractApplication.APPLICATION_USAGE_KEY).get(application);
        if (usage == null) return;
        // Getting duration of state by comparing with next event (if next event is of same type, we just generate two statistics)...
        for (int i = 0; i < usage.getEvents().size(); i++) {
            ApplicationEvent current = usage.getEvents().get(i);
            log.info("Considering usage event ", current);
            Date eventStartDate = current.getDate();
            Date eventEndDate = stopDate;

            if (i <  usage.getEvents().size() - 1) {
                ApplicationEvent next =  usage.getEvents().get(i + 1);
                eventEndDate = next.getDate();
            }

            if (eventStartDate.compareTo(stopDate) >= 0 || eventEndDate.compareTo(startDate) <= 0) {
                continue;
            }

            if (eventStartDate.compareTo(startDate) < 0) {
                eventStartDate = startDate;
            }
            if (eventEndDate.compareTo(stopDate) > 0) {
                eventEndDate = stopDate;
            }
            long duration = eventEndDate.getTime() - eventStartDate.getTime();
            Statistic statistic = new Statistic(ApplicationTransformer.statusFromLifecycle(current.getState()), usage.getApplicationId(), usage.getApplicationName(), dateFormat.format(eventStartDate), dateFormat.format(eventEndDate), duration, usage.getMetadata());
            log.info("Adding usage statistic to response ", statistic);
            list.add(statistic);
        }
    }

    private static final Set<Lifecycle> WORKING_LIFECYCLES = ImmutableSet.of(Lifecycle.RUNNING, Lifecycle.CREATED, Lifecycle.STARTING);
    
    @Override
    public List<Statistic> listMachineUsages(final String application, final String start, final String stop) {
        log.warn("Determining machine usage from events for application " + application);
        SimpleDateFormat dateFormat = new SimpleDateFormat(dateFormatPattern);

        final Date startDate = parseSafely(dateFormat, start, new Date(0));
        final Date stopDate = parseSafely(dateFormat, stop, new Date());

        if (startDate.compareTo(stopDate) >= 0) {
            return ImmutableList.of();
        }
        
        Set<LocationUsage> matches = ((LocalLocationManager) mgmt().getLocationManager()).getLocationUsage(new Predicate<LocationUsage>() {
            @Override
            public boolean apply(LocationUsage input) {
                LocationUsage.LocationEvent first = input.getEvents().get(0);
                if (stopDate.compareTo(first.getDate()) < 0) {
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
            retrieveMachineUsage(usage, start, stop, results);
        }
        return ImmutableList.of();
    }

    private void retrieveMachineUsage(LocationUsage usage, String startMarker, String stopMarker, List<Statistic> list) {
        log.warn("Determining usage from events for LocationEvent " + usage);
        SimpleDateFormat dateFormat = new SimpleDateFormat(dateFormatPattern);

        final Date startDate = parseSafely(dateFormat, startMarker, new Date(0));
        final Date stopDate = parseSafely(dateFormat, stopMarker, new Date());

        if (startDate.compareTo(stopDate) >= 0) {
            return;
        }

        if (usage == null) return;
        
        // Getting duration of state by comparing with next event (if next event is of same type, we just generate two statistics)...
        for (int i = 0; i < usage.getEvents().size(); i++) {
            LocationUsage.LocationEvent current = usage.getEvents().get(i);
            log.info("Considering usage event ", current);
            Date eventStartDate = current.getDate();
            Date eventEndDate = stopDate;

            if (i <  usage.getEvents().size() - 1) {
                LocationUsage.LocationEvent next =  usage.getEvents().get(i + 1);
                eventEndDate = next.getDate();
            }

            if (eventStartDate.compareTo(stopDate) >= 0 || eventEndDate.compareTo(startDate) <= 0) {
                continue;
            }

            if (eventStartDate.compareTo(startDate) < 0) {
                eventStartDate = startDate;
            }
            if (eventEndDate.compareTo(stopDate) > 0) {
                eventEndDate = stopDate;
            }
            long duration = eventEndDate.getTime() - eventStartDate.getTime();
            Statistic statistic = new Statistic(ApplicationTransformer.statusFromLifecycle(current.getState()), usage.getLocationId(), usage.getLocationId(), dateFormat.format(eventStartDate), dateFormat.format(eventEndDate), duration, usage.getMetadata());
            log.info("Adding usage statistic to response ", statistic);
            list.add(statistic);
        }
    }
}

package brooklyn.rest.transform;

import brooklyn.entity.Application;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.rest.domain.ApplicationSpec;
import brooklyn.rest.domain.ApplicationSummary;
import brooklyn.rest.domain.Status;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static brooklyn.rest.domain.Status.*;

public class ApplicationTransformer {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ApplicationTransformer.class);

    private final static Map<Status, Status> validTransitions =
            ImmutableMap.<Status, Status>builder()
                    .put(Status.UNKNOWN, Status.ACCEPTED)
                    .put(Status.ACCEPTED, Status.STARTING)
                    .put(Status.STARTING, Status.RUNNING)
                    .put(Status.RUNNING, Status.STOPPING)
                    .put(Status.STOPPING, Status.STOPPED)
                    .put(Status.STOPPED, Status.STARTING)
                    .build();

    public static final Function<? super Application, ApplicationSummary> FROM_APPLICATION = new Function<Application, ApplicationSummary>() {
        @Override
        public ApplicationSummary apply(Application input) {
            return summaryFromApplication(input);
        }
    };

    public static Status statusFromApplication(Application application) {
        if (application == null) return UNKNOWN;
        Lifecycle state = application.getAttribute(Attributes.SERVICE_STATE);
        if (state != null) return statusFromLifecycle(state);
        Boolean up = application.getAttribute(Startable.SERVICE_UP);
        if (up != null && up.booleanValue()) return RUNNING;
        return UNKNOWN;
    }


    public static Status statusFromLifecycle(Lifecycle state) {
        if (state == null) return UNKNOWN;
        switch (state) {
            case CREATED:
                return ACCEPTED;
            case STARTING:
                return STARTING;
            case RUNNING:
                return RUNNING;
            case STOPPING:
                return STOPPING;
            case STOPPED:
                return STOPPED;
            case DESTROYED:
            case ON_FIRE:
            default:
                return UNKNOWN;
        }
    }

    public static ApplicationSpec specFromApplication(Application application) {
        Collection<String> locations = Collections2.transform(application.getLocations(), new Function<Location, String>() {
            @Override
            @Nullable
            public String apply(@Nullable Location input) {
                return input.getId();
            }
        });
        // okay to have entities and config as null, as this comes from a real instance
        return new ApplicationSpec(application.getDisplayName(), application.getEntityType().getName(),
                null, locations, null);
    }

    public static ApplicationSummary summaryFromApplication(Application application) {
        Map<String, URI> links;
        if (application.getId() == null) {
            links = Collections.emptyMap();
        } else {
            links = ImmutableMap.of(
                    "self", URI.create("/v1/applications/" + application.getId()),
                    "entities", URI.create("/v1/applications/" + application.getId() + "/entities"));
        }

        return new ApplicationSummary(specFromApplication(application), statusFromApplication(application), links, application.getId());
    }
}

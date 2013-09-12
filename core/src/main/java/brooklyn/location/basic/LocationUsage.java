package brooklyn.location.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import brooklyn.entity.basic.Lifecycle;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 */
public class LocationUsage {
    
    public static class LocationEvent {
        private final Date date;
        private final Lifecycle state;
        private final String entityId;
        private final String entityType;
        private final String applicationId;

        public LocationEvent(Lifecycle state, String entityId, String entityType, String applicationId) {
            this.date = new Date();
            this.state = checkNotNull(state, "state");
            this.entityId = checkNotNull(entityId, "entityId");
            this.entityType = checkNotNull(entityType, "entityType");
            this.applicationId = checkNotNull(applicationId, "applicationId");
        }

        public Date getDate() {
            return date;
        }

        public Lifecycle getState() {
            return state;
        }

        public String getEntityId() {
            return entityId;
        }

        public String getEntityType() {
            return entityType;
        }

        public String getApplicationId() {
            return applicationId;
        }
    }
    
    private final String locationId;
    private final Map<String, String> metadata;
    private final List<LocationEvent> events = Collections.synchronizedList(Lists.<LocationEvent>newArrayList());

    public LocationUsage(String locationId, Map<String, String> metadata) {
        this.locationId = checkNotNull(locationId, "locationId");
        this.metadata = checkNotNull(metadata, "metadata");
    }

    public String getLocationId() {
        return locationId;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public List<LocationEvent> getEvents() {
        synchronized (events) {
            return ImmutableList.copyOf(events);
        }
    }

    public void addEvent(LocationEvent event) {
        events.add(checkNotNull(event, "event"));
    }
}

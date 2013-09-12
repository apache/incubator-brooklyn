package brooklyn.location.basic;

import brooklyn.entity.Application;
import brooklyn.entity.basic.Lifecycle;
import com.google.common.collect.ImmutableMap;

import java.util.Date;
import java.util.Map;

/**
 */
public class LocationEvent {
    private final Date date;
    private final Lifecycle event;
    private final String entityId;
    private final String entityType;
    private final String applicationId;
    private final Map<String, String> metadata;

    public LocationEvent(Lifecycle event, String entityId, String entityType, String applicationId, Map<String, String> metadata) {
        this.date = new Date();
        this.event = event;
        this.entityId = entityId;
        this.entityType = entityType;
        this.applicationId = applicationId;
        this.metadata = metadata;
    }

    public Date getDate() {
    
        return date;
    }

    public Lifecycle getEvent() {
        return event;
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

    public Map<String, String> getMetadata() {
        return metadata;
    }
}

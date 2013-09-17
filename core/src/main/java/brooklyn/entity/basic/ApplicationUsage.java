package brooklyn.entity.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 */
public class ApplicationUsage {
    
    public static class ApplicationEvent {
        private final Date date;
        private final Lifecycle state;

        public ApplicationEvent(Lifecycle state) {
            this.date = new Date();
            this.state = checkNotNull(state, "state");
        }

        public Date getDate() {
            return date;
        }

        public Lifecycle getState() {
            return state;
        }
        
        @Override
        public String toString() {
            return Objects.toStringHelper(this).add("date", date).add("state", state).toString();
        }
    }
    
    private final String applicationId;
    private final String applicationName;
    private final String entityType;
    private final Map<String, String> metadata;
    private final List<ApplicationEvent> events = Collections.synchronizedList(Lists.<ApplicationEvent>newArrayList());

    public ApplicationUsage(String applicationId, String applicationName, String entityType, Map<String, String> metadata) {
        this.applicationId = checkNotNull(applicationId, "applicationId");
        this.applicationName = checkNotNull(applicationName, "applicationName");
        this.entityType = checkNotNull(entityType, "entityType");
        this.metadata = checkNotNull(metadata, "metadata");
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getEntityType() {
        return entityType;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public List<ApplicationEvent> getEvents() {
        synchronized (events) {
            return ImmutableList.copyOf(events);
        }
    }

    public void addEvent(ApplicationEvent event) {
        events.add(checkNotNull(event, "event"));
    }
}

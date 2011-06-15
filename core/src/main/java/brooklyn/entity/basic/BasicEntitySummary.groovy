package brooklyn.entity.basic

import java.util.Collection

public class BasicEntitySummary implements EntitySummary {
    
    final String id;
    final String displayName;
    final String applicationId;
    final Collection<String> groupIds;

    public BasicEntitySummary(String id, String displayName, String applicationId, Collection<String> groups) {
        this.id = id;
        this.displayName = displayName;
        this.applicationId = applicationId;
        this.groupIds = groups;
    }
}

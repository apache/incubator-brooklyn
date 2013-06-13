package brooklyn.internal.storage.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;

import brooklyn.internal.storage.Serializer;
import brooklyn.internal.storage.impl.LocationSerializer.LocationPointer;
import brooklyn.location.Location;
import brooklyn.management.internal.LocalLocationManager;
import brooklyn.management.internal.ManagementContextInternal;

import com.google.common.base.Objects;

public class LocationSerializer implements Serializer<Location, LocationPointer> {

    public static class LocationPointer implements Serializable {
        private static final long serialVersionUID = -3358568001462543001L;
        
        final String id;
        
        public LocationPointer(String id) {
            this.id = checkNotNull(id, "id");
        }
        @Override public String toString() {
            return Objects.toStringHelper(this).add("id",  id).toString();
        }
        @Override public int hashCode() {
            return id.hashCode();
        }
        @Override public boolean equals(Object obj) {
            return (obj instanceof LocationPointer) && id.equals(((LocationPointer)obj).id);
        }
    }

    private final ManagementContextInternal managementContext;
    
    public LocationSerializer(ManagementContextInternal managementContext) {
        this.managementContext = managementContext;
    }

    @Override
    public LocationPointer serialize(Location orig) {
        return new LocationPointer(orig.getId());
    }

    @Override
    public Location deserialize(LocationPointer serializedForm) {
        Location result = ((LocalLocationManager)managementContext.getLocationManager()).getLocationEvenIfPreManaged(serializedForm.id);
        return checkNotNull(result, "no location with id %s", serializedForm.id);
    }
}

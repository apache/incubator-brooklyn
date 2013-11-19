package brooklyn.entity.database;

import brooklyn.event.AttributeSensor;

/** @deprecated since 0.7.0 use DatastoreMixins.DatastoreCommon */ @Deprecated 
public interface DatabaseNode extends DatastoreMixins.DatastoreCommon {

    /** @deprecated since 0.7.0 use DATASTORE_URL */ @Deprecated 
    public static final AttributeSensor<String> DB_URL = DATASTORE_URL;
    
}

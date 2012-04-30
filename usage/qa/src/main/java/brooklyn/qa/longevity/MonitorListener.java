package brooklyn.qa.longevity;

import brooklyn.qa.longevity.StatusRecorder.Record;

public interface MonitorListener {

    public static final MonitorListener NOOP = new MonitorListener() {
        @Override public void onRecord(Record record) {
        }
        @Override public void onFailure(Record record, String msg) {
        }
    };
    
    public void onRecord(Record record);
    
    public void onFailure(Record record, String msg);
}

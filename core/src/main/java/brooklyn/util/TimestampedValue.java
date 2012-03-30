package brooklyn.util;

import com.google.common.base.Objects;

public class TimestampedValue<T> {

    private final T value;
    private final long timestamp;
    
    public TimestampedValue(T value, long timestamp) {
        this.value = value;
        this.timestamp = timestamp;
    }
    
    public T getValue() {
        return value;
    }
    
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value, timestamp);
    }
    
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TimestampedValue)) {
            return false;
        }
        TimestampedValue<?> o = (TimestampedValue<?>) other;
        return o.getTimestamp() == timestamp && Objects.equal(o.getValue(), value);
    }
    
    @Override
    public String toString() {
        return "val="+value+"; timestamp="+timestamp;
    }
}

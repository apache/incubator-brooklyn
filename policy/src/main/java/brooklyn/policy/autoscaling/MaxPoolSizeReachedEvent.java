package brooklyn.policy.autoscaling;

import java.io.Serializable;

import com.google.common.base.Objects;

public class MaxPoolSizeReachedEvent implements Serializable {
    private static final long serialVersionUID = 1602627701360505190L;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        protected long maxAllowed;
        protected long currentPoolSize;
        protected long currentUnbounded;
        protected long maxUnbounded;
        protected long timeWindow;
        
        public Builder maxAllowed(long val) {
            this.maxAllowed = val; return this;
        }

        public Builder currentPoolSize(long val) {
            this.currentPoolSize = val; return this;
        }

        public Builder currentUnbounded(long val) {
            this.currentUnbounded = val; return this;
        }

        public Builder maxUnbounded(long val) {
            this.maxUnbounded = val; return this;
        }

        public Builder timeWindow(long val) {
            this.timeWindow = val; return this;
        }
        public MaxPoolSizeReachedEvent build() {
            return new MaxPoolSizeReachedEvent(this);
        }
    }
    
    private final long maxAllowed;
    private final long currentPoolSize;
    private final long currentUnbounded;
    private final long maxUnbounded;
    private final long timeWindow;
    
    protected MaxPoolSizeReachedEvent(Builder builder) {
        maxAllowed = builder.maxAllowed;
        currentPoolSize = builder.currentPoolSize;
        currentUnbounded = builder.currentUnbounded;
        maxUnbounded = builder.maxUnbounded;
        timeWindow = builder.timeWindow;
    }
    
    public long getMaxAllowed() {
        return maxAllowed;
    }
    
    public long getCurrentPoolSize() {
        return currentPoolSize;
    }
    
    public long getCurrentUnbounded() {
        return currentUnbounded;
    }
    
    public long getMaxUnbounded() {
        return maxUnbounded;
    }
    
    public long getTimeWindow() {
        return timeWindow;
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("maxAllowed", maxAllowed).add("currentPoolSize", currentPoolSize)
                .add("currentUnbounded", currentUnbounded).add("maxUnbounded", maxUnbounded)
                .add("timeWindow", timeWindow).toString();
    }
}

package brooklyn.entity.drivers;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import brooklyn.entity.drivers.DownloadsRegistry.DownloadTargets;
import brooklyn.util.MutableList;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class BasicDownloadTargets implements DownloadTargets {

    private static final DownloadTargets EMPTY = builder().build();
    
    public static DownloadTargets empty() {
        return EMPTY;
    }
    
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> primaries = Lists.newArrayList();
        private List<String> fallbacks = Lists.newArrayList();
        private boolean canContinueResolving = true;
        
        public Builder addAll(DownloadTargets other) {
            addPrimaries(other.getPrimaryLocations());
            addFallbacks(other.getFallbackLocations());
            return this;
        }
        
        public Builder addPrimary(String val) {
            checkNotNull(val, "val");
            if (!primaries.contains(val)) primaries.add(val);
            return this;
        }

        public Builder addPrimaries(Iterable<String> vals) {
            for (String val : checkNotNull(vals, "vals")) {
                addPrimary(val);
            }
            return this;
        }

        public Builder addFallback(String val) {
            checkNotNull(val, "val");
            if (!fallbacks.contains(val)) fallbacks.add(val);
            return this;
        }

        public Builder addFallbacks(Iterable<String> vals) {
            for (String val : checkNotNull(vals, "vals")) {
                addFallback(val);
            }
            return this;
        }

        public Builder canContinueResolving(boolean val) {
            canContinueResolving = val;
            return this;
        }
        
        public BasicDownloadTargets build() {
            return new BasicDownloadTargets(this);
        }
    }

    private final List<String> primaries;
    private final List<String> fallbacks;
    private final boolean canContinueResolving;
    
    protected BasicDownloadTargets(Builder builder) {
        primaries = ImmutableList.copyOf(builder.primaries);
        fallbacks = MutableList.<String>builder().addAll(builder.fallbacks).removeAll(builder.primaries).build().toImmutable();
        canContinueResolving = builder.canContinueResolving;
    }

    @Override
    public List<String> getPrimaryLocations() {
        return primaries;
    }

    @Override
    public List<String> getFallbackLocations() {
        return fallbacks;
    }

    @Override
    public boolean canContinueResolving() {
        return canContinueResolving;
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("primaries", primaries).add("fallbacks", fallbacks)
                .add("canContinueResolving", canContinueResolving).toString();
    }
}

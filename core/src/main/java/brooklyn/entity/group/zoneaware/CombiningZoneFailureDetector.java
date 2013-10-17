package brooklyn.entity.group.zoneaware;

import java.util.List;

import brooklyn.entity.Entity;
import brooklyn.entity.group.zoneaware.InterAvailabilityZoneDynamicCluster.ZoneFailureDetector;
import brooklyn.location.Location;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class CombiningZoneFailureDetector implements ZoneFailureDetector {

    public static CombiningZoneFailureDetector failIfAny(ZoneFailureDetector... detectors) {
        Predicate<List<Boolean>> joiner = new Predicate<List<Boolean>>() {
            @Override public boolean apply(List<Boolean> input) {
                return input.contains(Boolean.TRUE);
            }
        };
        return new CombiningZoneFailureDetector(joiner, detectors);
    }

    public static CombiningZoneFailureDetector failIfAll(ZoneFailureDetector... detectors) {
        Predicate<List<Boolean>> joiner = new Predicate<List<Boolean>>() {
            @Override public boolean apply(List<Boolean> input) {
                return input.contains(Boolean.TRUE) && !input.contains(Boolean.FALSE) && !input.contains(null);
            }
        };
        return new CombiningZoneFailureDetector(joiner, detectors);
    }

    private final Predicate<? super List<Boolean>> joiner;
    private final List<ZoneFailureDetector> detectors;

    protected CombiningZoneFailureDetector(Predicate<? super List<Boolean>> joiner, ZoneFailureDetector... detectors) {
        this.joiner = joiner;
        this.detectors = ImmutableList.copyOf(detectors);
    }
    
    @Override
    public void onStartupSuccess(Location loc, Entity entity) {
        for (ZoneFailureDetector detector : detectors) {
            detector.onStartupSuccess(loc, entity);
        }
    }

    @Override
    public void onStartupFailure(Location loc, Entity entity, Throwable cause) {
        for (ZoneFailureDetector detector : detectors) {
            detector.onStartupFailure(loc, entity, cause);
        }
    }

    @Override
    public boolean hasFailed(Location loc) {
        List<Boolean> opinions = Lists.newArrayList();
        for (ZoneFailureDetector detector : detectors) {
            detector.hasFailed(loc);
        }
        return joiner.apply(opinions);
    }
}

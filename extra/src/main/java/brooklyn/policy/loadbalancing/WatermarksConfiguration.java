package brooklyn.policy.loadbalancing;

import com.google.common.base.Preconditions;

/**
 * Temporary stub to resolve dependencies in ported LoadBalancingPolicy.
 * 
 * @author splodge
 */
public class WatermarksConfiguration {
    
    public final double nodeLowWaterMark;
    public final double nodeHighWaterMark;
    public final double poolLowWaterMark;
    public final double poolHighWaterMark;
    
    public WatermarksConfiguration(
            double nodeLowWatermark, double nodeHighWatermark,
            double poolLowWatermark, double poolHighWatermark) {

        Preconditions.checkArgument(nodeLowWatermark <= nodeHighWatermark, "Node low watermark cannot be greater than the high");
        Preconditions.checkArgument(poolLowWatermark <= poolHighWatermark, "Pool low watermark cannot be greater than the high");
        
        this.nodeLowWaterMark = nodeLowWatermark;
        this.nodeHighWaterMark = nodeHighWatermark;
        this.poolLowWaterMark = poolLowWatermark;
        this.poolHighWaterMark = poolHighWatermark;
    }
    
}
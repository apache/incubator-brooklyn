package brooklyn.policy.loadbalancing;

import java.util.Set;

/**
 * Ported from Monterey v3; irrelevant bits removed.
 * 
 * @author splodge
 */
public class PolicyUtilForPool<ContainerType, ItemType> {
    
    private final BalanceablePoolModel<ContainerType, ItemType> model;
    
    
    public PolicyUtilForPool (BalanceablePoolModel<ContainerType, ItemType> model) {
        this.model = model;
    }
    
    public ContainerType findColdestContainer(Set<ContainerType> excludedContainers) {
        return findColdestContainer(excludedContainers, null);
    }
    
//    public ContainerType findColdestContainer(Set<ContainerType> excludedContainers, LocationConstraint locationConstraint) {
//        double min = Double.MAX_VALUE;
//        ContainerType coldest = null;
//        
//        for (ContainerType n : model.getPoolContents()) {
//            if (excludedContainers.contains(n))
//                continue;
//            if (locationConstraint != null && !locationConstraint.isPermitted(model.getLocation(n)))
//                continue;
//            
//            double w = model.getTotalWorkrate(n);
//            if (w < min) {
//                min = w;
//                coldest = n;
//            }
//        }
//        return coldest;
//    }
    
    /**
     * Identifies the container with the maximum spare capacity (highThreshold - currentWorkrate),
     * returns null if none of the model's nodes has spare capacity.
     */
    public ContainerType findColdestContainer(Set<ContainerType> excludedContainers, LocationConstraint locationConstraint) {
        double maxSpareCapacity = 0;
        ContainerType coldest = null;
        
        for (ContainerType c : model.getPoolContents()) {
            if (excludedContainers.contains(c))
                continue;
            if (locationConstraint != null && !locationConstraint.isPermitted(model.getLocation(c)))
                continue;
            
            double spareCapacity = model.getHighThreshold(c) - model.getTotalWorkrate(c);
            if (spareCapacity > maxSpareCapacity) {
                maxSpareCapacity = spareCapacity;
                coldest = c;
            }
        }
        return coldest;
    }
    
//    public ContainerType findHottestContainer(Set<ContainerType> excludedContainers) {
//        double max = Double.MIN_VALUE;
//        ContainerType hottest = null;
//        
//        for (ContainerType n : model.getPoolContents()) {
//            if (excludedContainers.contains(n))
//                continue;
//            
//            double w = model.getTotalWorkrate(n);
//            if (w > max) {
//                max = w;
//                hottest = n;
//            }
//        }
//        return hottest;
//    }
    
    /**
     * Identifies the container with the maximum overshoot (currentWorkrate - highThreshold),
     * returns null if none of the model's  nodes has an overshoot.
     */
    public ContainerType findHottestContainer(Set<ContainerType> excludedContainers) {
        double maxOvershoot = 0;
        ContainerType hottest = null;
        
        for (ContainerType c : model.getPoolContents()) {
            if (excludedContainers.contains(c))
                continue;
            
            double overshoot = model.getTotalWorkrate(c) - model.getHighThreshold(c);
            if (overshoot > maxOvershoot) {
                maxOvershoot = overshoot;
                hottest = c;
            }
        }
        return hottest;
    }
    
}
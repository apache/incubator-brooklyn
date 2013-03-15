package brooklyn.policy;

/**
 * Publishes metrics for an entity, e.g. aggregating information from other sensors/entities.
 * 
 * Has some similarities to {@link Policy}. However, enrichers specifically do not invoke 
 * Effectors and should only function to publish new metrics
 */
public interface Enricher extends EntityAdjunct {
}

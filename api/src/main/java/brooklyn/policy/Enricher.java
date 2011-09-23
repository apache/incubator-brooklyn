package brooklyn.policy;

/**
 * Enrichers specifically do not invoke Effectors and should only function to publish new metrics
 */
public interface Enricher extends EntityAdjunct {
}

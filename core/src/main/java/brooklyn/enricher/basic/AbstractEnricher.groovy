package brooklyn.enricher.basic

import brooklyn.policy.Enricher;
import brooklyn.policy.basic.AbstractEntityAdjunct;

/**
* Base {@link Enricher} implementation; all enrichers should extend this or its children
*/
public abstract class AbstractEnricher extends AbstractEntityAdjunct implements Enricher {

}

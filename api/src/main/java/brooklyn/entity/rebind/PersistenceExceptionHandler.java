package brooklyn.entity.rebind;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.mementos.Memento;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;

import com.google.common.annotations.Beta;

/**
 * Handler called on all exceptions to do with persistence.
 * 
 * @author aled
 */
@Beta
public interface PersistenceExceptionHandler {

    void stop();

    void onGenerateLocationMementoFailed(Location location, Exception e);

    void onGenerateEntityMementoFailed(Entity entity, Exception e);
    
    void onGeneratePolicyMementoFailed(Policy policy, Exception e);
    
    void onGenerateEnricherMementoFailed(Enricher enricher, Exception e);

    void onPersistMementoFailed(Memento memento, Exception e);
    
    void onDeleteMementoFailed(String id, Exception e);
}

package brooklyn.entity.rebind.transformer;

import brooklyn.mementos.BrooklynMemento;

import com.google.common.annotations.Beta;

/**
 * Transforms the raw data of persisted state (e.g. of an entity).
 */
@Beta
public interface BrooklynMementoTransformer {

    public BrooklynMemento transform(BrooklynMemento input) throws Exception;
}

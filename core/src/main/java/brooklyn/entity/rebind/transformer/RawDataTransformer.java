package brooklyn.entity.rebind.transformer;

import com.google.common.annotations.Beta;

/**
 * Transforms the raw data of persisted state (e.g. of an entity).
 */
@Beta
public interface RawDataTransformer {

    public String transform(String input) throws Exception;
}

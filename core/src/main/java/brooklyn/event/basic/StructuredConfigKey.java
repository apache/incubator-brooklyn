package brooklyn.event.basic;

import java.util.Map;

public interface StructuredConfigKey {

    /** for internal use */
    Object applyValueToMap(Object value, @SuppressWarnings("rawtypes") Map target);
    
}

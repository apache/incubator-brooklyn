package brooklyn.location;

import java.util.Map;

public interface LocationDefinition {

    public String getId();
    public String getName();
    public String getSpec();
    public Map<String,Object> getConfig();

}
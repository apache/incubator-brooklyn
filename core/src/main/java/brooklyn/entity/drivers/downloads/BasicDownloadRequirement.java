package brooklyn.entity.drivers.downloads;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import brooklyn.entity.drivers.EntityDriver;
import brooklyn.entity.drivers.downloads.DownloadResolverManager.DownloadRequirement;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;

public class BasicDownloadRequirement implements DownloadRequirement {

    private final EntityDriver entityDriver;
    private final String addonName;
    private final Map<String, ?> properties;

    /**
     * Copies the given DownloadRequirement, but overriding the original properties with the given additional properties.
     */
    public static BasicDownloadRequirement copy(DownloadRequirement req, Map<String,?> additionalProperties) {
        Map<String,?> props = MutableMap.<String,Object>builder().putAll(req.getProperties()).putAll(additionalProperties).build();
        if (req.getAddonName() == null) {
            return new BasicDownloadRequirement(req.getEntityDriver(), props);
        } else {
            return new BasicDownloadRequirement(req.getEntityDriver(), req.getAddonName(), props);
        }
    }

    public BasicDownloadRequirement(EntityDriver driver) {
        this(driver, ImmutableMap.<String,Object>of());
    }
    
    public BasicDownloadRequirement(EntityDriver driver, Map<String, ?> properties) {
        this.entityDriver = checkNotNull(driver, "entityDriver");
        this.addonName = null;
        this.properties = checkNotNull(properties, "properties");
    }
    
    public BasicDownloadRequirement(EntityDriver entityDriver, String addonName, Map<String, ?> properties) {
        this.entityDriver = checkNotNull(entityDriver, "entityDriver");
        this.addonName = checkNotNull(addonName, "addonName");
        this.properties = checkNotNull(properties, "addonProperties");
    }

    @Override
    public EntityDriver getEntityDriver() {
        return entityDriver;
    }

    @Override
    public String getAddonName() {
        return addonName;
    }

    @Override
    public Map<String, ?> getProperties() {
        return properties;
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("driver", entityDriver).add("addon", addonName).omitNullValues().toString();
    }
}

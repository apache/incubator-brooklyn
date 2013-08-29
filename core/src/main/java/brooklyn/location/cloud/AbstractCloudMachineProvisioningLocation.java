package brooklyn.location.cloud;

import java.util.Collection;
import java.util.Map;

import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.LocationCreationUtils;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.internal.ssh.SshTool;

public abstract class AbstractCloudMachineProvisioningLocation extends AbstractLocation 
implements MachineProvisioningLocation<SshMachineLocation>, CloudLocationConfig 
{
   public AbstractCloudMachineProvisioningLocation() {
      super();
   }

    /** typically wants at least ACCESS_IDENTITY and ACCESS_CREDENTIAL */
    public AbstractCloudMachineProvisioningLocation(Map<?,?> conf) {
        super(conf);
    }

    /** uses reflection to create an object of the same type, assuming a Map constructor;
     * subclasses can extend and downcast the result */
    @Override
    public AbstractCloudMachineProvisioningLocation newSubLocation(Map<?,?> newFlags) {
        return LocationCreationUtils.newSubLocation(newFlags, this);
    }

    @Override
    public Map<String, Object> getProvisioningFlags(Collection<String> tags) {
        if (tags.size() > 0) {
            LOG.warn("Location {}, ignoring provisioning tags {}", this, tags);
        }
        return MutableMap.<String, Object>of();
    }

    // ---------------- utilities --------------------
    
    protected ConfigBag extractSshConfig(ConfigBag setup, ConfigBag alt) {
        ConfigBag sshConfig = new ConfigBag();
        
        if (setup.containsKey(PASSWORD)) {
            sshConfig.put(SshTool.PROP_PASSWORD, setup.get(PASSWORD));
        } else if (alt.containsKey(PASSWORD)) {
            sshConfig.put(SshTool.PROP_PASSWORD, alt.get(PASSWORD));
        }
        
        if (setup.containsKey(PRIVATE_KEY_DATA)) {
            sshConfig.put(SshTool.PROP_PRIVATE_KEY_DATA, setup.get(PRIVATE_KEY_DATA));
        } else if (setup.containsKey(PRIVATE_KEY_FILE)) {
            sshConfig.put(SshTool.PROP_PRIVATE_KEY_FILE, setup.get(PRIVATE_KEY_FILE));
        } else if (alt.containsKey(PRIVATE_KEY_DATA)) {
            sshConfig.put(SshTool.PROP_PRIVATE_KEY_DATA, alt.get(PRIVATE_KEY_DATA));
        }
        
        if (setup.containsKey(PRIVATE_KEY_PASSPHRASE)) {
            // NB: not supported in jclouds (but it is by our ssh tool)
            sshConfig.put(SshTool.PROP_PRIVATE_KEY_PASSPHRASE, setup.get(PRIVATE_KEY_PASSPHRASE));
        }

        // TODO extract other SshTool properties ?
        
        return sshConfig;
    }

}

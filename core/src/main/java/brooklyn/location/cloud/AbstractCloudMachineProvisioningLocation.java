package brooklyn.location.cloud;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import org.jclouds.compute.domain.NodeMetadata;

import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.internal.ssh.SshTool;

public abstract class AbstractCloudMachineProvisioningLocation extends AbstractLocation 
implements MachineProvisioningLocation<SshMachineLocation>, CloudLocationConfig 
{

    /** typically wants at least ACCESS_IDENTITY and ACCESS_CREDENTIAL */
    public AbstractCloudMachineProvisioningLocation(Map conf) {
        super(conf);
    }

    @Override
    public Map<String, Object> getProvisioningFlags(Collection<String> tags) {
        if (tags.size() > 0) {
            LOG.warn("Location {}, ignoring provisioning tags {}", this, tags);
        }
        return MutableMap.<String, Object>of();
    }

    // ---------------- utilities --------------------
    
    protected Map extractSshConfig(ConfigBag setup, NodeMetadata node) throws IOException {
        ConfigBag sshConfig = new ConfigBag();
        
        if (setup.get(PASSWORD) != null) {
            sshConfig.put(SshTool.PROP_PASSWORD, setup.get(PASSWORD));
        } else if (node!=null && node.getCredentials().getPassword() != null) {
            sshConfig.put(SshTool.PROP_PASSWORD, node.getCredentials().getPassword());
        }
        
        if (setup.containsKey(PRIVATE_KEY_DATA)) {
            sshConfig.put(SshTool.PROP_PRIVATE_KEY_DATA, setup.get(PRIVATE_KEY_DATA));
        } else if (setup.containsKey(PRIVATE_KEY_FILE)) {
            sshConfig.put(SshTool.PROP_PRIVATE_KEY_FILE, setup.get(PRIVATE_KEY_FILE));
        } else if (node!=null && node.getCredentials().getPrivateKey() != null) {
            sshConfig.put(SshTool.PROP_PRIVATE_KEY_DATA, node.getCredentials().getPrivateKey());
        }
        
        if (setup.containsKey(PRIVATE_KEY_PASSPHRASE)) {
            // NB: not supported in jclouds (but it is by our ssh tool)
            sshConfig.put(SshTool.PROP_PRIVATE_KEY_PASSPHRASE, setup.get(PRIVATE_KEY_PASSPHRASE));
        }

        // TODO extract other SshTool properties ?
        
        // TODO could return the config bag ?
        return sshConfig.getAllConfig();
    }

}

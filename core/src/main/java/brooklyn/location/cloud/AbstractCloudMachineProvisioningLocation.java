/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.location.cloud;

import java.util.Collection;
import java.util.Map;

import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.basic.AbstractLocation;
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
        return newSubLocation(getClass(), newFlags);
    }

    public AbstractCloudMachineProvisioningLocation newSubLocation(Class<? extends AbstractCloudMachineProvisioningLocation> type, Map<?,?> newFlags) {
        // TODO should be able to use ConfigBag.newInstanceExtending; would require moving stuff around to api etc
        // TODO was previously `return LocationCreationUtils.newSubLocation(newFlags, this)`; need to retest on CloudStack etc
        return getManagementContext().getLocationManager().createLocation(LocationSpec.create(type)
                .parent(this)
                .configure(getLocalConfigBag().getAllConfig()) // FIXME Should this just be inherited?
                .configure(newFlags));
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

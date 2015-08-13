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
package brooklyn.location.cloud.names;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.trait.HasShortName;

import brooklyn.location.cloud.CloudLocationConfig;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;

import com.google.common.annotations.Beta;
import com.google.common.base.CharMatcher;

/** 
 * Implements <b>most</b> of {@link CloudMachineNamer},
 * leaving just one method -- {@link #generateNewIdOfLength(int)} --
 * for subclasses to provide.
 * <p>
 * {@link CloudLocationConfig#VM_NAME_MAX_LENGTH} is used to find the VM length, 
 * unless {@link #getCustomMaxNameLength(ConfigBag)} is overridden or
 * {@link #setDefaultMachineNameMaxLength(int)} invoked on the instance supplied.
 */
public abstract class AbstractCloudMachineNamer implements CloudMachineNamer {

    int defaultMachineNameMaxLength = CloudLocationConfig.VM_NAME_MAX_LENGTH.getDefaultValue();
    int defaultMachineNameSaltLength = CloudLocationConfig.VM_NAME_SALT_LENGTH.getDefaultValue();
    protected String separator = "-";

    public String generateNewMachineUniqueName(ConfigBag setup) {
        return generateNewIdReservingLength(setup, 0);
    }
    
    public String generateNewMachineUniqueNameFromGroupId(ConfigBag setup, String groupId) {
        int availSaltLength = getMaxNameLength(setup) - (groupId.length() + separator.length());
        int requestedSaltLength = getLengthForMachineUniqueNameSalt(setup, false);
        if (availSaltLength <= 0 || requestedSaltLength <= 0) {
            return groupId;
        }
            
        return sanitize(groupId + separator + Identifiers.makeRandomId(Math.min(requestedSaltLength, availSaltLength))).toLowerCase();
    }

    public String generateNewGroupId(ConfigBag setup) {
        return sanitize(generateNewIdReservingLength(setup, getLengthForMachineUniqueNameSalt(setup, true))).toLowerCase();
    }

    protected String generateNewIdReservingLength(ConfigBag setup, int lengthToReserve) {
        int len = getMaxNameLength(setup);
        // decrement by e.g. 9 chars because jclouds adds that (dash plus 8 for hex id)
        len -= lengthToReserve;
        if (len<=0) return "";
        return Strings.maxlen(generateNewIdOfLength(setup, len), len);
    }
    
    /** Method for subclasses to provide to construct the context-specific part of an identifier,
     * for use in {@link #generateNewGroupId()} and {@link #generateNewMachineUniqueName()}.
     * 
     * @param maxLengthHint an indication of the maximum length permitted for the ID generated,
     * supplied for implementations which wish to use this information to decide what to truncate.
     * (This class will truncate any return values longer than this.) 
     */
    protected abstract String generateNewIdOfLength(ConfigBag setup, int maxLengthHint);

    /** Returns the max length of a VM name for the cloud specified in setup;
     * this value is typically decremented by 9 to make room for jclouds labels;
     * delegates to {@link #getCustomMaxNameLength()} when 
     * {@link CloudLocationConfig#VM_NAME_MAX_LENGTH} is not set */
    public int getMaxNameLength(ConfigBag setup) {
        if (setup.containsKey(CloudLocationConfig.VM_NAME_MAX_LENGTH)) {
            // if a length is set explicitly, use that (but intercept default behaviour)
            return setup.get(CloudLocationConfig.VM_NAME_MAX_LENGTH);
        }
        
        Integer custom = getCustomMaxNameLength(setup);
        if (custom!=null) return custom;
        
        // return the default
        return defaultMachineNameMaxLength;  
    }
    
    // sometimes we create salt string, sometimes jclouds does
    public int getLengthForMachineUniqueNameSalt(ConfigBag setup, boolean includeSeparator) {
        int saltLen;
        if (setup.containsKey(CloudLocationConfig.VM_NAME_SALT_LENGTH)) {
            saltLen = setup.get(CloudLocationConfig.VM_NAME_SALT_LENGTH);
        } else {
            // default value comes from key, but custom default can be set
            saltLen = defaultMachineNameSaltLength;
        }
        
        if (saltLen>0 && includeSeparator)
            saltLen += separator.length();
        
        return saltLen;
    }
    
    public AbstractCloudMachineNamer setDefaultMachineNameMaxLength(int defaultMaxLength) {
        this.defaultMachineNameMaxLength = defaultMaxLength;
        return this;
    }

    /** Number of chars to use or reserve for the machine identifier when constructing a group identifier;
     * jclouds for instance uses "-" plus 8 */
    public AbstractCloudMachineNamer setDefaultMachineNameSeparatorAndSaltLength(String separator, int defaultMachineUniqueNameSaltLength) {
        this.separator = separator;
        this.defaultMachineNameSaltLength = defaultMachineUniqueNameSaltLength;
        return this;
    }
    
    /** Method for overriding to provide custom logic when an explicit config key is not set for the machine length. */
    public Integer getCustomMaxNameLength(ConfigBag setup) {
        return null;
    }

    protected static String shortName(Object x) {
        if (x instanceof HasShortName) {
            return ((HasShortName)x).getShortName();
        }
        if (x instanceof Entity) {
            return ((Entity)x).getDisplayName();
        }
        return x.toString();
    }

    @Beta //probably won't live here long-term
    public static String sanitize(String s) {
        return CharMatcher.inRange('A', 'Z')
                .or(CharMatcher.inRange('a', 'z'))
                .or(CharMatcher.inRange('0', '9'))
                .negate()
                .trimAndCollapseFrom(s, '-');
    }
}

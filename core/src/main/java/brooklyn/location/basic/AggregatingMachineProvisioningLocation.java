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
package brooklyn.location.basic;

import static com.google.common.base.Preconditions.checkState;

import java.io.Closeable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.stream.Streams;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Takes a list of other provisioners, and round-robins across them when obtaining a machine.
 */
public class AggregatingMachineProvisioningLocation<T extends MachineLocation> extends AbstractLocation 
        implements MachineProvisioningLocation<T>, Closeable {

    private Object lock;
    
    @SetFromFlag
    protected List<MachineProvisioningLocation<T>> provisioners;
    
    @SetFromFlag
    protected Map<T, MachineProvisioningLocation<T>> inUse;

    protected final AtomicInteger obtainCounter = new AtomicInteger();
    
    public AggregatingMachineProvisioningLocation() {
        this(Maps.newLinkedHashMap());
    }
    
    public AggregatingMachineProvisioningLocation(Map properties) {
        super(properties);

        if (isLegacyConstruction()) {
            init();
        }
    }

    @Override
    public void init() {
        super.init();
    }
    
    @Override
    public String toVerboseString() {
        return Objects.toStringHelper(this).omitNullValues()
                .add("id", getId()).add("name", getDisplayName())
                .add("provisioners", provisioners)
                .toString();
    }

    @Override
    public void configure(Map properties) {
        if (lock == null) {
            lock = new Object();
            provisioners = Lists.<MachineProvisioningLocation<T>>newArrayList();
            inUse = Maps.<T, MachineProvisioningLocation<T>>newLinkedHashMap();
        }
        super.configure(properties);
    }
    
    @Override
    public AggregatingMachineProvisioningLocation<T> newSubLocation(Map<?,?> newFlags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        for (MachineProvisioningLocation<?> provisioner : provisioners) {
            if (provisioner instanceof Closeable) {
                Streams.closeQuietly((Closeable)provisioner);
            }
        }
    }
    
    public T obtain() throws NoMachinesAvailableException {
        return obtain(Maps.<String,Object>newLinkedHashMap());
    }
    
    @Override
    public T obtain(Map<?,?> flags) throws NoMachinesAvailableException {
        checkState(provisioners.size() > 0, "no provisioners!");
        int index = obtainCounter.getAndIncrement();
        for (int i = 0; i < provisioners.size(); i++) {
            MachineProvisioningLocation<T> provisioner = provisioners.get(index++ % provisioners.size());
            try {
                T machine = provisioner.obtain(flags);
                inUse.put(machine, provisioner);
                return machine;
            } catch (NoMachinesAvailableException e) {
                // move on; try next
            }
        }
        throw new NoMachinesAvailableException("No machines available in "+toString());
    }

    @Override
    public void release(T machine) {
        MachineProvisioningLocation<T> provisioner = inUse.remove(machine);
        if (provisioner != null) {
            provisioner.release(machine);
        } else {
            throw new IllegalStateException("Request to release machine "+machine+", but this machine is not currently allocated");
        }
    }

    @Override
    public Map<String,Object> getProvisioningFlags(Collection<String> tags) {
        return Maps.<String,Object>newLinkedHashMap();
    }
}

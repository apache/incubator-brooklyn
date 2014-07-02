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
package brooklyn.entity.rebind;

import java.util.List;
import java.util.Map;

import brooklyn.basic.BrooklynObject;
import brooklyn.entity.Entity;
import brooklyn.location.Location;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class RecordingRebindExceptionHandler extends RebindExceptionHandlerImpl {

    protected final List<Exception> loadMementoFailures = Lists.newArrayList();
    protected final Map<String, Exception> createFailures = Maps.newLinkedHashMap();
    protected final Map<BrooklynObject, Exception> rebindFailures = Maps.newLinkedHashMap();
    protected final Map<BrooklynObject, Exception> manageFailures = Maps.newLinkedHashMap();
    protected final Map<String, Exception> notFoundFailures = Maps.newLinkedHashMap();
    protected Exception failed;
    
    public RecordingRebindExceptionHandler(RebindManager.RebindFailureMode danglingRefFailureMode, RebindManager.RebindFailureMode rebindFailureMode) {
        super(builder().danglingRefFailureMode(danglingRefFailureMode).rebindFailureMode(rebindFailureMode));
    }

    @Override
    public void onLoadMementoFailed(BrooklynObjectType type, String msg, Exception e) {
        loadMementoFailures.add(new IllegalStateException("problem loading "+type+" memento: "+msg, e));
        super.onLoadMementoFailed(type, msg, e);
    }
    
    @Override
    public Entity onDanglingEntityRef(String id) {
        return super.onDanglingEntityRef(id);
    }

    @Override
    public Location onDanglingLocationRef(String id) {
        return super.onDanglingLocationRef(id);
    }

    @Override
    public void onCreateFailed(BrooklynObjectType type, String id, String instanceType, Exception e) {
        createFailures.put(id, new IllegalStateException("problem creating location "+id+" of type "+instanceType, e));
        super.onCreateFailed(type, id, instanceType, e);
    }

    @Override
    public void onNotFound(BrooklynObjectType type, String id) {
        notFoundFailures.put(id, new IllegalStateException(type+" '"+id+"' not found"));
        super.onNotFound(type, id);
    }
    
    @Override
    public void onRebindFailed(BrooklynObjectType type, BrooklynObject instance, Exception e) {
        rebindFailures.put(instance, new IllegalStateException("problem rebinding "+type+" "+instance.getId()+" ("+instance+")", e));
        super.onRebindFailed(type, instance, e);
    }

    @Override
    public void onManageFailed(BrooklynObjectType type, BrooklynObject instance, Exception e) {
        manageFailures.put(instance, new IllegalStateException("problem managing "+type+" "+instance.getId()+" ("+instance+")", e));
        super.onManageFailed(type, instance, e);
    }

    @Override
    public void onDone() {
        super.onDone();
    }

    @Override
    public RuntimeException onFailed(Exception e) {
        failed = e;
        return super.onFailed(e);
    }
}

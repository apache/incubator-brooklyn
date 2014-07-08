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
package brooklyn.internal.storage.impl.hazelcast;

import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntityProxyImpl;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;

import java.io.IOException;

import static java.lang.String.format;

class EntityStreamSerializer implements StreamSerializer {

    private HazelcastDataGrid hazelcastDataGrid;

    public EntityStreamSerializer(HazelcastDataGrid hazelcastDataGrid) {
        this.hazelcastDataGrid = hazelcastDataGrid;
    }

    @Override
    public Object read(ObjectDataInput in) throws IOException {
        EntityId id = in.readObject();
        Entity entity = hazelcastDataGrid.getManagementContext().getEntityManager().getEntity(id.getId());
        if (entity == null) {
            throw new IllegalStateException(format("Entity with id [%s] is not found", id));
        }
        return java.lang.reflect.Proxy.newProxyInstance(
                entity.getClass().getClassLoader(),
                entity.getClass().getInterfaces(),
                new EntityProxyImpl(entity));
    }

    @Override
    public void write(ObjectDataOutput out, Object object) throws IOException {
        Entity entity = (Entity) object;
        out.writeObject(new EntityId(entity.getId()));
    }

    @Override
    public int getTypeId() {
        return 5000;
    }

    @Override
    public void destroy() {
        //no-op
    }
}

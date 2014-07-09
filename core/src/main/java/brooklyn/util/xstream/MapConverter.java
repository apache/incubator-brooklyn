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
package brooklyn.util.xstream;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.core.ReferencingMarshallingContext;
import com.thoughtworks.xstream.io.ExtendedHierarchicalStreamWriterHelper;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;

/** equivalent to super, but cleaner methods, overridable, logging, and some retries */
public class MapConverter extends com.thoughtworks.xstream.converters.collections.MapConverter {

    private static final Logger log = LoggerFactory.getLogger(MapConverter.class);
    
    public MapConverter(Mapper mapper) {
        super(mapper);
    }

    @SuppressWarnings({ "rawtypes" })
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        Map map = (Map) source;
        try {
            for (Iterator iterator = map.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry entry = (Map.Entry) iterator.next();
                marshalEntry(writer, context, entry);
            }
        } catch (ConcurrentModificationException e) {
            log.debug("Map "
                // seems there is no non-deprecated way to get the path...
                + (context instanceof ReferencingMarshallingContext ? "at "+((ReferencingMarshallingContext)context).currentPath() : "")
                + "["+source+"] modified while serializing; will fail, and retry may be attempted");
            throw e;
            // would be nice to attempt to re-serialize being slightly more defensive, as code below;
            // but the code above may have written partial data so that is dangerous, we could have corrupted output. 
            // if we could mark and restore in the output stream then we could do this below (but we don't have that in our stream),
            // or we could try this copying code in the first instance (but that's slow);
            // error is rare most of the time (e.g. attribute being updated) so we bail on this whole attempt
            // and simply try serializing the map-owner (e.g. an entity) again.
//            ImmutableList entries = ImmutableList.copyOf(map.entrySet());
//            for (Iterator iterator = entries.iterator(); iterator.hasNext();) {
//                Map.Entry entry = (Map.Entry) iterator.next();
//                marshalEntry(writer, context, entry);                
//            }
        }
    }

    protected String getEntryNodeName() { return mapper().serializedClass(Map.Entry.class); }
    
    protected void marshalEntry(HierarchicalStreamWriter writer, MarshallingContext context, Map.Entry entry) {
        ExtendedHierarchicalStreamWriterHelper.startNode(writer, getEntryNodeName(), Map.Entry.class);

        writeItem(entry.getKey(), context, writer);
        writeItem(entry.getValue(), context, writer);

        writer.endNode();
    }

    protected void populateMap(HierarchicalStreamReader reader, UnmarshallingContext context, Map map) {
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            unmarshalEntry(reader, context, map);
            reader.moveUp();
        }
    }

    protected void unmarshalEntry(HierarchicalStreamReader reader, UnmarshallingContext context, Map map) {
        reader.moveDown();
        Object key = readItem(reader, context, map);
        reader.moveUp();

        reader.moveDown();
        Object value = readItem(reader, context, map);
        reader.moveUp();

        map.put(key, value);
    }

}

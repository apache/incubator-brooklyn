package brooklyn.internal.storage.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.internal.storage.DataGrid;
import brooklyn.internal.storage.Serializer;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;

/**
 * A simple implementation of datagrid backed by in-memory (unpersisted) maps, within a single JVM.
 * 
 * @author aled
 */
public class InmemoryDatagrid implements DataGrid {

    private static final Logger LOG = LoggerFactory.getLogger(InmemoryDatagrid.class);

    private final Map<String,Map<Object,Object>> rawMaps = Maps.newLinkedHashMap();
    private final Map<String,Map<Object,Object>> maps = Maps.newLinkedHashMap();
    private final ConcurrentMap<Class<?>, Serializer<?,?>> serializers = Maps.newConcurrentMap();
    private final ConcurrentMap<Class<?>, Serializer<?,?>> deserializers = Maps.newConcurrentMap();
    private final GlobalSerializer globalSerializer = new GlobalSerializer();
    
    @VisibleForTesting
    public InmemoryDatagrid cloneData() {
        InmemoryDatagrid result = new InmemoryDatagrid();
        
        synchronized (maps) {
            for (Map.Entry<String,Map<Object,Object>> entry : rawMaps.entrySet()) {
                ConcurrentMap<Object, Object> map = result.getMap(entry.getKey());
                try {
                    for (Map.Entry<Object, Object> entry2 : entry.getValue().entrySet()) {
                        map.put(serializeAndDeserialize(entry2.getKey()), serializeAndDeserialize(entry2.getValue()));
                    }
                } catch (Exception e) {
                    LOG.error("Error cloning map "+entry.getKey()+" - rethrowing", e);
                    throw Exceptions.propagate(e);
                }
            }
        }
        
        return result;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <K, V> ConcurrentMap<K, V> getMap(String id) {
        synchronized (maps) {
            ConcurrentMap<K, V> result = (ConcurrentMap<K, V>) maps.get(id);
            if (result == null) {
                ConcurrentMap<K,V> rawMap = Maps.<K,V>newConcurrentMap();
                result = new ConcurrentMapAcceptingNullVals2<K,V>(rawMap, globalSerializer);
                rawMaps.put(id, (Map)rawMap);
                maps.put(id, (Map)result);
            }
            return result;
        }
    }
    
    @Override
    public void remove(String id) {
        synchronized (maps) {
            maps.remove(id);
        }
    }

    @Override
    public void terminate() {
        synchronized (maps) {
            maps.clear();
    }
 
    @Override
    public <S,T> void registerSerializer(Serializer<S,T> serializer, Class<S> originalClazz, Class<T> serializedClazz) {
        serializers.put(originalClazz, serializer);
        deserializers.put(serializedClazz, serializer);
    }

    private class GlobalSerializer implements Serializer<Object,Object> {

        @Override
        public Object serialize(Object orig) {
            if (orig == null) {
                return null;
            } else {
                Class<?> origClazz = orig.getClass();
                for (Map.Entry<Class<?>,Serializer<?,?>> entry : serializers.entrySet()) {
                    if (entry.getKey().isAssignableFrom(origClazz)) {
                        return ((Serializer)entry.getValue()).serialize(orig);
                    }
                }
                return orig;
            }
        }

        @Override
        public Object deserialize(Object serializedForm) {
            if (serializedForm == null) {
                return null;
            } else {
                Class<?> serializedFormClazz = serializedForm.getClass();
                for (Map.Entry<Class<?>,Serializer<?,?>> entry : deserializers.entrySet()) {
                    if (entry.getKey().isAssignableFrom(serializedFormClazz)) {
                        return ((Serializer)entry.getValue()).deserialize(serializedForm);
                    }
                }
                return serializedForm;
            }
        }
    }
    
    private <T> T serializeAndDeserialize(T obj) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ObjectOutputStream oOut = new CustomObjectOutputStream(out);
            oOut.writeObject(obj);
            ObjectInputStream oIn = new CustomObjectInputStream(new ByteArrayInputStream(out.toByteArray()));
            return (T) oIn.readObject();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    private static class ConvertedRef implements Serializable {
        private static final long serialVersionUID = 5555077847688776972L;
        
        final Object ref;
        
        ConvertedRef(Object ref) {
            this.ref = ref;
        }
    }
    
    // TODO If a value in the map is a list of locations or some such, then they don't get auto-converted
    // by the map.put wrapper. So do it here.
    private class CustomObjectOutputStream extends ObjectOutputStream {
        protected CustomObjectOutputStream(OutputStream out) throws IOException, SecurityException {
            super(out);
            enableReplaceObject(true);
        }
        
        @Override
        protected Object replaceObject(Object obj) throws IOException {
            Object result = globalSerializer.serialize(obj);
            if (result == obj) {
                return result;
            } else {
                return new ConvertedRef(result);
            }
        }
    }
    
    private class CustomObjectInputStream extends ObjectInputStream {

        public CustomObjectInputStream(InputStream in) throws IOException {
            super(in);
            enableResolveObject(true);
        }
        
        @Override
        protected Object resolveObject(Object obj) throws IOException {
            if (obj instanceof ConvertedRef) {
                return globalSerializer.deserialize(((ConvertedRef)obj).ref);
            } else {
                return obj;
            }
        }
    }
}

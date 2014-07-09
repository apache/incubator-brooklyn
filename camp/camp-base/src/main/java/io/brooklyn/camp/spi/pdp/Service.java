package io.brooklyn.camp.spi.pdp;

import io.brooklyn.util.yaml.Yamls;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;

import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class Service {

    String name;
    String description;
    String serviceType;
    
    List<ServiceCharacteristic> characteristics;
    
    Map<String,Object> customAttributes;
    
    @SuppressWarnings("unchecked")
    public static Service of(Map<String, Object> service) {
        Map<String,Object> fields = MutableMap.copyOf(service);
        
        Service result = new Service();
        result.name = (String) fields.remove("name");
        result.description = (String) fields.remove("description");
        // FIXME _type needed in lots of places
        result.serviceType = (String) (String) Yamls.removeMultinameAttribute(fields, "service_type", "serviceType", "type");
        
        result.characteristics = new ArrayList<ServiceCharacteristic>();
        Object chars = fields.remove("characteristics");
        if (chars instanceof Iterable) {
            for (Object req: (Iterable<Object>)chars) {
                if (req instanceof Map) {
                    result.characteristics.add(ServiceCharacteristic.of((Map<String,Object>) req));
                } else {
                    throw new IllegalArgumentException("characteristics should be a map, not "+req.getClass());
                }
            }
        } else if (chars!=null) {
            // TODO "map" short form
            throw new IllegalArgumentException("services body should be iterable, not "+chars.getClass());
        }
        
        result.customAttributes = fields;
        
        return result;
    }
    
    public String getName() {
        return name;
    }
    public String getDescription() {
        return description;
    }
    public String getServiceType() {
        return serviceType;
    }
    public List<ServiceCharacteristic> getCharacteristics() {
        return ImmutableList.copyOf(characteristics);
    }
    public Map<String, Object> getCustomAttributes() {
        return ImmutableMap.copyOf(customAttributes);
    }
    
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

}

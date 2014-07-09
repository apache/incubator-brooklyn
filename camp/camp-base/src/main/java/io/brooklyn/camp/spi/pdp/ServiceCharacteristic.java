package io.brooklyn.camp.spi.pdp;

import io.brooklyn.util.yaml.Yamls;

import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;

import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableMap;

public class ServiceCharacteristic {

    String name;
    String description;
    String characteristicType;
    
    Map<String,Object> customAttributes;
    
    public static ServiceCharacteristic of(Map<String, Object> req) {
        Map<String,Object> attrs = MutableMap.copyOf(req);
        
        ServiceCharacteristic result = new ServiceCharacteristic();
        result.name = (String) attrs.remove("name");
        result.description = (String) attrs.remove("description");
        result.characteristicType = (String) Yamls.removeMultinameAttribute(attrs, "characteristicType", "type");
        
        // TODO fulfillment
        
        result.customAttributes = attrs;
        
        return result;
    }

    public String getName() {
        return name;
    }
    public String getDescription() {
        return description;
    }
    public String getCharacteristicType() {
        return characteristicType;
    }
    
    public Map<String, Object> getCustomAttributes() {
        return ImmutableMap.copyOf(customAttributes);
    }
    
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

}

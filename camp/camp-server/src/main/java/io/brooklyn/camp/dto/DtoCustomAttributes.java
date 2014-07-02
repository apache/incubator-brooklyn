package io.brooklyn.camp.dto;

import java.util.Map;

import brooklyn.util.collections.MutableMap;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.common.collect.ImmutableMap;

public class DtoCustomAttributes extends DtoBase {

    private Map<String,Object> customAttributes = new MutableMap<String, Object>();

    protected DtoCustomAttributes() {}
    
    public DtoCustomAttributes(Map<String,?> customAttributes) {
        this.customAttributes = customAttributes==null ? ImmutableMap.<String, Object>of() : ImmutableMap.copyOf(customAttributes);
    }
    
    @JsonIgnore
    public Map<String, Object> getCustomAttributes() {
        return customAttributes;
    }

    // --- json ---
    
    @JsonInclude(Include.NON_EMPTY)
    @JsonAnyGetter
    private Map<String,Object> any() {
        return customAttributes;
    }
    @JsonAnySetter
    private void set(String name, Object value) {
        customAttributes.put(name, value);
    }

    // --- building ---

    protected void newInstanceCustomAttributes(Map<String,?> customAttributes) {
        if (customAttributes!=null)
            this.customAttributes.putAll(customAttributes);
    }
    
}

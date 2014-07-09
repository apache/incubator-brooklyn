package io.brooklyn.camp.rest.util;

import brooklyn.util.exceptions.Exceptions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class CampJsons {

    public static String prettyJson(Object o) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw Exceptions.propagate(e);
        }
    }
    
}

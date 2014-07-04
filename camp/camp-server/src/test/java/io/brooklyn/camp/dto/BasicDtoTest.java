package io.brooklyn.camp.dto;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Tests identity methods and custom attributes for DTO, including Jackson JSON serialization */
public class BasicDtoTest {

    private static final Logger log = LoggerFactory.getLogger(BasicDtoTest.class);
    
    @Test
    public void testSimple() throws IOException {
        DtoCustomAttributes l = new DtoCustomAttributes(null);
        
        JsonNode t = tree(l);
        Assert.assertEquals(t.size(), 0);
        Assert.assertTrue(l.getCustomAttributes()==null || l.getCustomAttributes().isEmpty());
        
        Assert.assertEquals(l, new ObjectMapper().readValue(t.toString(), DtoCustomAttributes.class));
    }

    @Test
    public void testCustomAttrs() throws IOException {
        DtoCustomAttributes l = new DtoCustomAttributes(MutableMap.of("bar", "bee"));
        
        JsonNode t = tree(l);
        Assert.assertEquals(t.size(), 1);
        Assert.assertEquals(t.get("bar").asText(), l.getCustomAttributes().get("bar"));
        
        Assert.assertEquals(l, new ObjectMapper().readValue(t.toString(), DtoCustomAttributes.class));
    }

    @Test
    public void testIdentity() throws IOException {
        DtoCustomAttributes l1 = new DtoCustomAttributes(null);
        DtoCustomAttributes l2 = new DtoCustomAttributes(MutableMap.of("bar", "bee"));
        DtoCustomAttributes l2o = new DtoCustomAttributes(MutableMap.of("bar", "bee"));
        
        Assert.assertEquals(l1, l1);
        Assert.assertEquals(l2, l2);
        Assert.assertEquals(l2, l2o);
        Assert.assertNotEquals(l1, l2);
        
        Assert.assertEquals(l1.hashCode(), l1.hashCode());
        Assert.assertEquals(l2.hashCode(), l2.hashCode());
        Assert.assertEquals(l2.hashCode(), l2o.hashCode());
        Assert.assertNotEquals(l1.hashCode(), l2.hashCode());
    }
    
    public static JsonNode tree(Object l) {
        try {
            ObjectMapper m = new ObjectMapper();
            String s = m.writeValueAsString(l);
            log.info(l.toString()+" -> "+s);
            JsonNode t = m.readTree(s);
            return t;
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

}

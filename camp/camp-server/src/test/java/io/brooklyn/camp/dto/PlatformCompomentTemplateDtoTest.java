package io.brooklyn.camp.dto;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.rest.util.DtoFactory;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.test.mock.web.MockWebPlatform;
import junit.framework.Assert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

public class PlatformCompomentTemplateDtoTest {

    private static final Logger log = LoggerFactory.getLogger(PlatformCompomentTemplateDtoTest.class);
    
    @Test
    public void testAppServerPct() {
        CampPlatform p = MockWebPlatform.newPlatform();
        DtoFactory f = new DtoFactory(p, "");
        
        PlatformComponentTemplate t = MockWebPlatform.APPSERVER;
        PlatformComponentTemplateDto dto = f.adapt(t);
        
        log.info("Web PCT serialized as: "+BasicDtoTest.tree(dto));
        Assert.assertEquals(dto.getName(), t.getName());
        Assert.assertNotNull(dto.getCreatedAsString());
        Assert.assertTrue(dto.getCreatedAsString().startsWith("20"));
    }
    
}

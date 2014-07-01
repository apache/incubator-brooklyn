package io.brooklyn.camp.dto;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.rest.util.DtoFactory;
import io.brooklyn.camp.spi.ApplicationComponentTemplate;
import io.brooklyn.camp.test.mock.web.MockWebPlatform;
import junit.framework.Assert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

public class ApplicationCompomentTemplateDtoTest {

    private static final Logger log = LoggerFactory.getLogger(ApplicationCompomentTemplateDtoTest.class);
    
    @Test
    public void testAppServerPct() {
        CampPlatform p = MockWebPlatform.newPlatform();
        DtoFactory f = new DtoFactory(p, "");
        
        ApplicationComponentTemplate t = MockWebPlatform.WAR;
        ApplicationComponentTemplateDto dto = f.adapt(t);
        
        log.info("War PCT serialized as: "+BasicDtoTest.tree(dto));
        Assert.assertEquals(dto.getName(), t.getName());
        Assert.assertNotNull(dto.getCreatedAsString());
        Assert.assertTrue(dto.getCreatedAsString().startsWith("20"));
    }
    
}

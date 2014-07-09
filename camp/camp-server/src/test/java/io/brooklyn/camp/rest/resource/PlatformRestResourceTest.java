package io.brooklyn.camp.rest.resource;

import io.brooklyn.camp.dto.PlatformComponentTemplateDto;
import io.brooklyn.camp.dto.PlatformDto;
import io.brooklyn.camp.test.fixture.AbstractRestResourceTest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

public class PlatformRestResourceTest extends AbstractRestResourceTest {

    private static final Logger log = LoggerFactory.getLogger(PlatformRestResourceTest.class);
    
    @Test
    public void testPlatformIncludesList() {
        PlatformDto p = load(PlatformRestResource.CAMP_URI_PATH, PlatformDto.class);
        PlatformComponentTemplateDto pct = load(p.getPlatformComponentTemplates().get(0).getHref(), PlatformComponentTemplateDto.class);
        log.debug("Loaded PCT via REST: "+pct);
        Assert.assertNotNull(pct.getName());
    }
        
}

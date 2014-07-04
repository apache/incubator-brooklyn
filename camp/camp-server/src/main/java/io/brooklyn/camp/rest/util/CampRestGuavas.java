package io.brooklyn.camp.rest.util;

import io.brooklyn.camp.spi.AbstractResource;

import com.google.common.base.Function;

public class CampRestGuavas {

    public static final Function<AbstractResource,String> IDENTITY_OF_REST_RESOURCE = 
            new Function<AbstractResource,String>() {
                public String apply(AbstractResource input) { return input.getId(); }
            };

}

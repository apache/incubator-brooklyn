package brooklyn.rest.filter;

import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseFilter;

public class CorsFilter
        implements ContainerResponseFilter {

    public ContainerResponse filter(ContainerRequest requestContext,
                                    ContainerResponse responseContext) {
        responseContext.getHttpHeaders().add("Access-Control-Allow-Origin", "*");
        responseContext.getHttpHeaders().add("Access-Control-Allow-Headers", "origin, content-type, accept, authorization");
        responseContext.getHttpHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
        responseContext.getHttpHeaders().add("Access-Control-Max-Age", "1209599");
        return responseContext;
    }

}

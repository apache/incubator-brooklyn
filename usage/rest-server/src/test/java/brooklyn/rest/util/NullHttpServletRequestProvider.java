package brooklyn.rest.util;

import java.lang.reflect.Type;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;

import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.core.spi.component.ComponentScope;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.InjectableProvider;

@Provider
public class NullHttpServletRequestProvider implements InjectableProvider<Context, Type> { 
    public Injectable<HttpServletRequest> getInjectable(ComponentContext ic, 
            Context a, Type c) { 
        if (HttpServletRequest.class == c) { 
            return new Injectable<HttpServletRequest>() {
                public HttpServletRequest getValue() { return null; }
            }; 
        } else 
            return null; 
    } 
    public ComponentScope getScope() { 
        return ComponentScope.Singleton; 
    } 
} 

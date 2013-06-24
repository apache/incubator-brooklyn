package brooklyn.rest.security;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.management.ManagementContext;
import brooklyn.rest.security.provider.DelegatingSecurityProvider;

import com.sun.jersey.core.util.Base64;

/**
 * Provides basic HTTP authentication.
 */
public class BrooklynPropertiesSecurityFilter implements Filter {
    
    /** session attribute set for authenticated users; for reference
     * (but should not be relied up to confirm authentication, as
     * the providers may impose additional criteria such as timeouts,
     * or a null user (no login) may be permitted) */
    public static final String AUTHENTICATED_USER_SESSION_ATTRIBUTE = "brooklyn.user";
    
    private static final Logger log = LoggerFactory.getLogger(BrooklynPropertiesSecurityFilter.class);
    
    protected ManagementContext mgmt;
    protected DelegatingSecurityProvider provider;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (provider==null) {
            log.warn("No security provider available: disallowing web access to brooklyn");
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        
        if (authenticate((HttpServletRequest)request)) {
            if (handleLogout((HttpServletRequest)request)) {
                // do nothing here, fall through to below
            } else {
                chain.doFilter(request, response);
                return;
            }
        }
        
        ((HttpServletResponse) response).setHeader("WWW-Authenticate","Basic realm=\"brooklyn\"");
        ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    protected boolean authenticate(HttpServletRequest request) {
        if (provider.isAuthenticated( request.getSession() ))
            return true;
        
        String user = null, pass = null;
        String authorization=request.getHeader("Authorization");
        if (authorization!=null) {
            String userpass=Base64.base64Decode(authorization.substring(6));
            user=userpass.substring(0,userpass.indexOf(":"));
            pass=userpass.substring(userpass.indexOf(":")+1);
        }
        
        if (provider.authenticate(request.getSession(), user, pass)) {
            log.debug("Web API authenticated "+request.getSession()+" for user "+user);
            if (user!=null) {
                request.getSession().setAttribute(AUTHENTICATED_USER_SESSION_ATTRIBUTE, user);
            }
            return true;
        }
        
        return false;
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
        mgmt = (ManagementContext) config.getServletContext().getAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT);
        provider = new DelegatingSecurityProvider(mgmt);
    }
    
    @Override
    public void destroy() {
    }

    protected boolean handleLogout(HttpServletRequest request) {
        if ("/logout".equals(request.getRequestURI()) || "/v1/logout".equals(request.getRequestURI())) {
            log.info("Web API logging out "+request.getSession()+" for user "+
                    request.getSession().getAttribute(AUTHENTICATED_USER_SESSION_ATTRIBUTE));
            provider.logout(request.getSession());
            request.getSession().removeAttribute(AUTHENTICATED_USER_SESSION_ATTRIBUTE);
            request.getSession().invalidate();
            return true;
        }
        return false;
    }

}

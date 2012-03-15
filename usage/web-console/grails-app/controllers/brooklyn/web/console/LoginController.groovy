package brooklyn.web.console

import grails.converters.JSON

import javax.security.auth.login.AccountExpiredException
import javax.servlet.http.HttpServletResponse

import org.codehaus.groovy.grails.plugins.springsecurity.SpringSecurityUtils
import org.codehaus.groovy.grails.web.context.ServletContextHolder
import org.springframework.security.authentication.CredentialsExpiredException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.LockedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.GrantedAuthorityImpl
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.WebAttributes
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

import brooklyn.config.BrooklynServiceAttributes;

class LoginController {

    /**
     * Dependency injection for the authenticationTrustResolver.
     */
    def authenticationTrustResolver

    /**
     * Dependency injection for the springSecurityService.
     */
    def springSecurityService

    /**
     * Default action; redirects to 'defaultTargetUrl' if logged in, /login/auth otherwise.
     */
    def index = {
        if (springSecurityService.isLoggedIn()) {
            redirect uri: SpringSecurityUtils.securityConfig.successHandler.defaultTargetUrl
        }
        else {
            redirect action: auth, params: params
        }
    }

    //logs the given user in
    private void autoLogin(username) {
        def user = SecurityUser.findByUsername(username)
        List auths = user.authorities.collect {
            new GrantedAuthorityImpl(it.authority)
        }
        def grailsUser = new org.springframework.security.core.userdetails.User(
            user.username, // String username, String password, 
            "",
            true, true, // boolean enabled, boolean accountNonExpired,
            true, true, // boolean credentialsNonExpired, boolean accountNonLocked, 
            auths //Collection<? extends GrantedAuthority> authorities);
            );
        def authToken =  new UsernamePasswordAuthenticationToken(grailsUser, '', auths)
        SecurityContextHolder.context.authentication = authToken
    }
    
    /**
     * Show the login page.
     */
    def auth = {
        def config = SpringSecurityUtils.securityConfig
        //ideally we'd do this on start of any new session, but not clear how to do it
        //(this probably isn't the best workaround to allow tests to access in any case!) 
        if (!springSecurityService.isLoggedIn()) {
            //for unit tests
            def autologinUser = ServletContextHolder.servletContext?.getAttribute(BrooklynServiceAttributes.BROOKLYN_AUTOLOGIN_USERNAME);
            if (autologinUser) {
                autoLogin(autologinUser)
            }
        }

        if (springSecurityService.isLoggedIn()) {
            redirect uri: config.successHandler.defaultTargetUrl
            return
        }

        String view = 'auth'
        String postUrl = "${request.contextPath}${config.apf.filterProcessesUrl}"
        render view: view, model: [postUrl: postUrl,
                                   rememberMeParameter: config.rememberMe.parameter]
    }

    /**
     * The redirect action for Ajax requests. 
     */
    def authAjax = {
        response.setHeader 'Location', SpringSecurityUtils.securityConfig.auth.ajaxLoginFormUrl
        response.sendError HttpServletResponse.SC_UNAUTHORIZED
    }

    /**
     * Show denied page.
     */
    def denied = {
        if (springSecurityService.isLoggedIn() &&
                authenticationTrustResolver.isRememberMe(SCH.context?.authentication)) {
            // have cookie but the page is guarded with IS_AUTHENTICATED_FULLY
            redirect action: full, params: params
        }
    }

    /**
     * Login page for users with a remember-me cookie but accessing a IS_AUTHENTICATED_FULLY page.
     */
    def full = {
        def config = SpringSecurityUtils.securityConfig
        render view: 'auth', params: params,
            model: [hasCookie: authenticationTrustResolver.isRememberMe(SCH.context?.authentication),
                    postUrl: "${request.contextPath}${config.apf.filterProcessesUrl}"]
    }

    /**
     * Callback after a failed login. Redirects to the auth page with a warning message.
     */
    def authfail = {

        def username = session[UsernamePasswordAuthenticationFilter.SPRING_SECURITY_LAST_USERNAME_KEY]
        String msg = ''
        def exception = session[WebAttributes.AUTHENTICATION_EXCEPTION]
        if (exception) {
            if (exception instanceof AccountExpiredException) {
                msg = SpringSecurityUtils.securityConfig.errors.login.expired
            }
            else if (exception instanceof CredentialsExpiredException) {
                msg = SpringSecurityUtils.securityConfig.errors.login.passwordExpired
            }
            else if (exception instanceof DisabledException) {
                msg = SpringSecurityUtils.securityConfig.errors.login.disabled
            }
            else if (exception instanceof LockedException) {
                msg = SpringSecurityUtils.securityConfig.errors.login.locked
            }
            else {
                msg = SpringSecurityUtils.securityConfig.errors.login.fail
            }
        }

        if (springSecurityService.isAjax(request)) {
            render([error: msg] as JSON)
        }
        else {
            flash.message = msg
            redirect action: auth, params: params
        }
    }

    /**
     * The Ajax success redirect url.
     */
    def ajaxSuccess = {
        render([success: true, username: springSecurityService.authentication.name] as JSON)
    }

    /**
     * The Ajax denied redirect url.
     */
    def ajaxDenied = {
        render([error: 'access denied'] as JSON)
    }
}

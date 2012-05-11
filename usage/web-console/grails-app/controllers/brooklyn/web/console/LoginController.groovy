package brooklyn.web.console

import grails.converters.JSON

import javax.servlet.http.HttpServletResponse

import brooklyn.web.console.security.WebConsoleSecurity

class LoginController {

    private String getRedirectTarget(boolean includePrefix) {
        String result = "/dashboard/"; 
        if (includePrefix) result = createLinkTo(dir:result)
        return result;
    }
    
    /**
     * Default action; redirects to 'defaultTargetUrl' if logged in, /login/auth otherwise.
     */
    def index = {
        if (WebConsoleSecurity.getInstance().isAuthenticated(session) || tryAuthenticate()) {
            redirect uri: getRedirectTarget(false);
        } else {
            redirect action: auth, params: params
        }
    }

    private boolean tryAuthenticate() {
        if (params.j_username) {
            return WebConsoleSecurity.getInstance().authenticate(session, params.j_username, params.j_password)
        }
        return false;
    }
    /**
     * Show the login page.
     */
    def auth = {
        if (WebConsoleSecurity.getInstance().isAuthenticated(session)) {
            redirect uri: getRedirectTarget(false)
            return
        }

        String view = 'auth'
        String postUrl = createLinkTo(dir:"login")
        //TODO support following to the originally requested URL
        String postAfterUrl = getRedirectTarget(true)
        render view: view, model: [postUrl: postUrl,
                                   rememberMeParameter: true]
    }

    /**
     * The redirect action for Ajax requests. 
     */
    def authAjax = {
//        response.setHeader 'Location', SpringSecurityUtils.securityConfig.auth.ajaxLoginFormUrl
        response.sendError HttpServletResponse.SC_UNAUTHORIZED
    }

    /**
     * Show denied page.
     */
    def denied = {
//        if (springSecurityService.isLoggedIn() &&
//                authenticationTrustResolver.isRememberMe(SCH.context?.authentication)) {
//            // have cookie but the page is guarded with IS_AUTHENTICATED_FULLY
//            redirect action: full, params: params
//        }
    }

    /**
     * Login page for users with a remember-me cookie but accessing a IS_AUTHENTICATED_FULLY page.
     */
    def full = {
//        def config = SpringSecurityUtils.securityConfig
//        render view: 'auth', params: params,
//            model: [hasCookie: authenticationTrustResolver.isRememberMe(SCH.context?.authentication),
//                    postUrl: "${request.contextPath}${config.apf.filterProcessesUrl}"]
    }

    /**
     * Callback after a failed login. Redirects to the auth page with a warning message.
     */
    def authfail = {
//
//        def username = session[UsernamePasswordAuthenticationFilter.SPRING_SECURITY_LAST_USERNAME_KEY]
//        String msg = ''
//        def exception = session[WebAttributes.AUTHENTICATION_EXCEPTION]
//        if (exception) {
//            if (exception instanceof AccountExpiredException) {
//                msg = SpringSecurityUtils.securityConfig.errors.login.expired
//            }
//            else if (exception instanceof CredentialsExpiredException) {
//                msg = SpringSecurityUtils.securityConfig.errors.login.passwordExpired
//            }
//            else if (exception instanceof DisabledException) {
//                msg = SpringSecurityUtils.securityConfig.errors.login.disabled
//            }
//            else if (exception instanceof LockedException) {
//                msg = SpringSecurityUtils.securityConfig.errors.login.locked
//            }
//            else {
//                msg = SpringSecurityUtils.securityConfig.errors.login.fail
//            }
//        }
//
//        if (springSecurityService.isAjax(request)) {
//            render([error: msg] as JSON)
//        }
//        else {
            flash.message = msg
            redirect action: auth, params: params
//        }
    }

    /**
     * The Ajax success redirect url.
     */
    def ajaxSuccess = {
        render([success: true] as JSON)
    }

    /**
     * The Ajax denied redirect url.
     */
    def ajaxDenied = {
        render([error: 'access denied'] as JSON)
    }
}

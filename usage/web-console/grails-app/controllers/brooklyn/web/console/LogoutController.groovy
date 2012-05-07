package brooklyn.web.console

import brooklyn.web.console.security.WebConsoleSecurity

class LogoutController {

    /**
     * Index action. Redirects to the Spring security logout uri.
     */
    def index = {
        WebConsoleSecurity.getInstance().logout(session)
        redirect controller: 'login';
    }
    
}

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.web.console.security.WebConsoleSecurity

class SecurityFilters {
   public static final Logger log = LoggerFactory.getLogger(SecurityFilters.class);
   
   def filters = {
       loginCheck(controller:'*', action:'*') {
           before = {
              if (!WebConsoleSecurity.getInstance().isAuthenticated(session) && !controllerName.equals('login')) {
                  log.info("redirecting ${session} from ${controllerName} to login page")
                  redirect(controller:'login')
                  return false
               }
           }
       }
   }
}

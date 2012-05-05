package brooklyn.web.console

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DashboardController {

    public static final Logger LOG = LoggerFactory.getLogger(this);
            
    def index = {
        LOG.debug("loading dashboard for {}", session)
    }
    
}

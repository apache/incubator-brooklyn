package brooklyn.entity.webapp

import brooklyn.entity.basic.UsesJava
import brooklyn.event.basic.BasicConfigKey
import brooklyn.util.flags.SetFromFlag

interface JavaWebAppService extends WebAppService, UsesJava {

	@SetFromFlag("war")
	public static final BasicConfigKey<String> ROOT_WAR = [ String, "wars.root", "WAR file to deploy as the ROOT, as URL (supporting file: and classpath: prefixes)" ]

    /**
     * @deprecated will be deleted in 0.5.
     */
    @Deprecated
	public static final BasicConfigKey<String> WAR = ROOT_WAR;

    @SetFromFlag("wars")
	public static final BasicConfigKey<List> NAMED_WARS = [ List, "wars.named", 
        "Archives files to deploy preserving their names for access as the path, as URL (supporting file: and classpath: prefixes)" ]

    /**
     * @deprecated will be deleted in 0.5.  use flag 'wars' now; kept for compatibility
     */
    @Deprecated
    @SetFromFlag("deployments")
    public static final BasicConfigKey<List> NAMED_DEPLOYMENTS = NAMED_WARS;
    
}

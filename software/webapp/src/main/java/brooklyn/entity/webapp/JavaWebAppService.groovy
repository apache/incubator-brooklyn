package brooklyn.entity.webapp

import brooklyn.entity.java.UsesJava
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
	public static final BasicConfigKey<List<String>> NAMED_WARS = [ List, "wars.named", 
        "Archive files to deploy, as URL strings (supporting file: and classpath: prefixes); context (path in user-facing URL) will be inferred by name" ]
    
    @SetFromFlag("warsByContext")
    public static final BasicConfigKey<Map<String,String>> WARS_BY_CONTEXT = [ Map, "wars.by.context",
        "Map of context keys (path in user-facing URL, typically without slashes) to archives (e.g. WARs by URL) to deploy, supporting file: and classpath: prefixes)" ]

    /**
     * @deprecated will be deleted in 0.5.  use flag 'wars' now; kept for compatibility
     */
    @Deprecated
    @SetFromFlag("deployments")
    public static final BasicConfigKey<List> NAMED_DEPLOYMENTS = NAMED_WARS;
}

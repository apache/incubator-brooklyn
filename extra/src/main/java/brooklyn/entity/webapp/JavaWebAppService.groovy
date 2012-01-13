package brooklyn.entity.webapp

import brooklyn.entity.basic.UsesJava
import brooklyn.event.basic.BasicConfigKey
import brooklyn.util.flags.SetFromFlag

interface JavaWebAppService extends WebAppService, UsesJava {

	@SetFromFlag("war")
	public static final BasicConfigKey<String> ROOT_WAR = [ String, "wars.root", "WAR file to deploy as the ROOT, as URL (supporting file: and classpath: prefixes)" ]
	@Deprecated
	public static final BasicConfigKey<String> WAR = ROOT_WAR;
	@SetFromFlag("wars")
	public static final BasicConfigKey<List> NAMED_WARS = [ List, "wars.named", "WAR files to deploy preserving their names for access as the path, as URL (supporting file: and classpath: prefixes)" ]
	
}

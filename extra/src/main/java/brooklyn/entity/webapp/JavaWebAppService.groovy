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
	public static final BasicConfigKey<String> NAMED_WARS = [ String, "wars.named", "WAR files to deploy with their given names, as URL (supporting file: and classpath: prefixes)" ]
	
}

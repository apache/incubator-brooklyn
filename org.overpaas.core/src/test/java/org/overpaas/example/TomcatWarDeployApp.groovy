package org.overpaas.example

import org.codehaus.groovy.tools.shell.Groovysh
import org.codehaus.groovy.tools.shell.IO
import org.codehaus.groovy.tools.shell.util.NoExitSecurityManager
import org.overpaas.console.EntityNavigationUtils
import org.overpaas.core.locations.SshMachineLocation
import org.overpaas.core.types.common.AbstractOverpaasApplication
import org.overpaas.web.tomcat.TomcatCluster
import org.overpaas.web.tomcat.TomcatNode
/** starts some tomcat nodes, on localhost, using ssh;
 * Copied from SimpleTomcatApp.groovy.
 * Get hello.war from 
    http://tomcat.apache.org/tomcat-7.0-doc/appdev/sample/
 * (and rename it!)
 * @author sam
 */
public class TomcatWarDeployApp extends AbstractOverpaasApplication {
	
	TomcatCluster tc = new TomcatCluster(displayName:'MyTomcat', initialSize:3, this);

	public static void main(String[] args) {
        
		def app = new TomcatWarDeployApp()

        app.tc.war = "/tmp/hello.war"
		app.tc.initialSize = 2  //override initial size
		
		EntityNavigationUtils.dump(app, "before start:  ")
		
		app.start(location:new 
			SshMachineLocation(name:'london', host:'localhost')
		)
		
		EntityNavigationUtils.dump(app, "after start:  ")

		Thread t = []
		t.start {
			while (!t.isInterrupted()) {
				Thread.sleep 5000
				app.entities.values().each { 
                    if (it in TomcatNode) {
						println ""+it+": "+it.jmxTool?.getChildrenAttributesWithTotal("Catalina:type=GlobalRequestProcessor,name=\"*\"")
						println "    "+it.getJmxSensors()
					}
				}
			}
		}
		
		println "waiting for readln then will kill the tomcats"
		System.in.read()
		t.interrupt()
		
		//TODO find a better way to shutdown a cluster?
		println "shutting down..."
		app.entities.values().each { if (it in TomcatNode) it.shutdown() }
		System.exit(0)
	}
	
}

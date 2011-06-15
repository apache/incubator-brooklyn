package org.overpaas.locations

import java.io.File;

import org.overpaas.entities.Entity;

public abstract class SshBasedJavaWebAppSetup extends SshBasedJavaAppSetup {

	public SshBasedJavaWebAppSetup(Entity entity) {
		super(entity);
	}

	public abstract String getDeployScript(String filename);

	/**
     * Copies f to loc:$installsBaseDir and invokes this.getDeployScript
     * for further processing on server.
     */
	//TODO should take an input stream?
    public void deploy(File f, SshMachineLocation loc) {
        String target = installsBaseDir + "/" + f.getName()
		log.debug "Tomcat Deploy - Copying file {} to $loc {}", f.getAbsolutePath(), target
        int copySuccess = loc.copyTo f, target
        String deployScript = getDeployScript(target)
        if (deployScript) {
            int result = loc.run(out:System.out, deployScript)
            if (result) {
                log.error "Failed to deploy $f to $loc"
            } else {
                log.debug "Deployed $f to $loc"
            }
        }
    }
        
}

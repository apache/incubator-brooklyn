/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.demo;

import java.util.Map;

import brooklyn.entity.webapp.tomcat.Tomcat7SshDriver;
import brooklyn.entity.webapp.tomcat.TomcatServerImpl;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.MutableMap;

/** CumulusRDF Tomcat driver. */
public class CumulusRDFTomcatSshDriver extends Tomcat7SshDriver {

    public CumulusRDFTomcatSshDriver(TomcatServerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    /** Customise with our copied config file. */
    public void customize() {
        super.customize();

        copyTemplate("classpath://cumulus.yaml", "cumulus.yaml");
    }


    /** Add property pointing to cumulus.yaml config file. */
    protected Map getCustomJavaSystemProperties() {
        return MutableMap.of("cumulusrdf.config-file", getRunDir() + "/cumulus.yaml");
    }

}

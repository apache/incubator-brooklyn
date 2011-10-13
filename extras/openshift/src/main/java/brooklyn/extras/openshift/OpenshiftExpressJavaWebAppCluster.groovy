package brooklyn.extras.openshift

import brooklyn.event.basic.BasicConfigKey;
import java.util.Collection
import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractService
import brooklyn.entity.trait.Startable
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.event.basic.BasicConfigKey
import brooklyn.extras.openshift.OpenshiftExpressAccess.OpenshiftExpressApplicationAccess
import brooklyn.location.Location
import brooklyn.util.SshBasedAppSetup
import brooklyn.util.internal.duplicates.ExecUtils

import com.google.common.base.Preconditions
import com.google.common.collect.Iterables

class OpenshiftExpressJavaWebAppCluster extends AbstractEntity implements Startable {

    public OpenshiftExpressJavaWebAppCluster(Map flags=[:], Entity owner=null) {
        super(flags, owner)
        setConfigIfValNonNull(JavaWebApp.WAR, flags.war)
        setConfigIfValNonNull(APP_NAME, flags.appName)
        setAttribute(AbstractService.SERVICE_STATUS, "uninitialized")
    }

    public static final BasicConfigKey<String> APP_NAME = [ String, "appName", "System name for uniquely referring to application; defaults to Brooklyn999999 " ]
    
    public String getAppName() {
        def appName = getConfig(APP_NAME);
        if (appName) return appName;
        appName = "Brooklyn"+getId();
        //hyphens not allowed
        if (appName.indexOf("-")>=0) appName = appName.substring(0, appName.indexOf("-"));
        return appName;
    }
    
    /** accepts e.g. "echo hi > /tmp/a \n echo ho > /tmp/b" */
    public static int execScriptBlocking(String script) {
        println "exec system process:\n"+script
        //this seems the best way to get paths set properly (login shell invocation)
        return ExecUtils.execBlocking("/bin/bash", "-cl", script.trim().split("\n").join(" && "));
    }
    
    boolean shouldDestroy = false;
    
    public void start(Collection<Location> locations) {
//        setAttribute(SERVICE_STATE, Lifecycle.STARTING)
//        attributePoller = new AttributePoller(this)
//        
//        preStart()
        startInLocation locations
//        setAttribute(SERVICE_STATE, Lifecycle.STARTED)
//
//        initSensors()
//        postStart()
//
//        setAttribute(SERVICE_STATE, Lifecycle.RUNNING)
    }

    public void startInLocation(Collection<Location> locations) {
        Preconditions.checkArgument locations.size() == 1
        Location location = Iterables.getOnlyElement(locations)
        startInLocation(location)
    }

    public void restart() {
        //TODO
    }
    public void stop() {
        //TODO
    }
    
    public void startInLocation(OpenshiftLocation ol) {
        locations << ol
        
        String war = getConfig(JavaWebApp.WAR);
        if (!war) throw new IllegalStateException("A WAR file is required to start ${this}")
                
        OpenshiftExpressApplicationAccess osa = new OpenshiftExpressApplicationAccess(username:ol.username, password:ol.password, appName:getAppName())
        //check status of app
        def user = osa.getUserInfo().getData();
        def userInfo = user.user_info
        def appInfo = user.app_info[getAppName()];
        if (appInfo==null) {
            //create app if necessary
            println "LOG creating app "+getAppName()
            shouldDestroy = true;
            osa.create();
            
            user = osa.getUserInfo().getData();
            appInfo = user.app_info[getAppName()];
        }
        
        //checkout app
        String gitUrl = "ssh://"+appInfo.uuid+"@"+getAppName()+"-"+userInfo.namespace+"."+userInfo.rhc_domain+"/~/git/"+getAppName()+".git/"
        String openshiftDir = SshBasedAppSetup.BROOKLYN_HOME_DIR+"/"+application.id+"/openshift-"+id;
        String openshiftGitDir = openshiftDir + "/git";
        
        println "LOG gitting app "+getAppName()
        //modify to have WAR file there
        //commit and push
        //TODO the sleep is inelegant but we need to wait for DNS to propagate 
        //(rhc is a bit smarter, it loops until name is available; should do the same here)
        int code = execScriptBlocking("""
mkdir -p ${openshiftGitDir}
cd ${openshiftGitDir}
sleep 15
git clone ${gitUrl}
cd ${getAppName()}
git rm -rf *
mkdir deployments
cp ${war} deployments/ROOT.war
touch deployments/ROOT.war.dodeploy
git add deployments
git commit -m "brooklyn automated project restructure for deployment of WAR file"
git push
""");
        //should now (or soon) be running
        if (code!=0) throw new IllegalStateException("Failed to deploy ${this} app ${getAppName()} (${war}): code ${code}")
        
        println "LOG app launched "+getAppName()

        //add support for DynamicWebAppCluster.startInLocation(Openshift)
    }

    //TODO should be a sensor, namespace should be discovered, and URL
    public String getWebAppAddress() {
        return "http://${getAppName()}-brooklyn.rhcloud.com/"
    }
    
    public void destroy() {
        //FIXME should use sensor for whether post-execution destruction is desired
        if (shouldDestroy) {
            println "LOG destroying app "+getAppName()
            OpenshiftLocation ol = Iterables.getOnlyElement(locations)
            OpenshiftExpressApplicationAccess osa = new OpenshiftExpressApplicationAccess(username:ol.username, password:ol.password, appName:getAppName());
            osa.destroy();
        }
    }
    
}

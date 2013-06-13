package brooklyn.extras.openshift

import brooklyn.event.basic.BasicConfigKey;
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.trait.Startable
import brooklyn.entity.webapp.JavaWebAppService
import brooklyn.event.basic.BasicConfigKey
import brooklyn.extras.openshift.OpenshiftExpressAccess.OpenshiftExpressApplicationAccess
import brooklyn.location.Location
import brooklyn.util.internal.duplicates.ExecUtils

import com.google.common.base.Preconditions
import com.google.common.collect.Iterables

class OpenshiftExpressJavaWebAppCluster extends AbstractEntity implements Startable, JavaWebAppService {

    private static final Logger log = LoggerFactory.getLogger(OpenshiftExpressJavaWebAppCluster.class)
    
    public static final BasicConfigKey<String> APP_NAME = [ String, "appName", "System name for uniquely referring to application; defaults to Brooklyn999999 " ]
                    
    public OpenshiftExpressJavaWebAppCluster(Map flags=[:], Entity parent=null) {
        super(flags, parent)
        setConfigIfValNonNull(ROOT_WAR, flags.war)
        setConfigIfValNonNull(APP_NAME, flags.appName)
        setAttribute(AbstractService.SERVICE_STATUS, "uninitialized")
    }

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
        //wrap each non-continued line in { command; } combined with && is the most useful way,
        //allowing for if tests etc
        String command = "";
        boolean continuation = false;
        script.trim().split("\n").each {
            if (!continuation) {
                if (command.length()>0) command += " && ";
                command += "{ ";
                it = it.trim();                
            } else {
                //just trim right (TODO more efficient, refactored method)
                while (it.length()>0 && it.charAt(it.length()-1)<=' ') it = it.substring(0, it.length()-1);
            }
            command += it;
            if (it.endsWith("\\")) continuation=true;
            else {
                continuation = false;
                if (!it.endsWith(";")) command+=";";
                command += " }" 
            }
        }
        log.info "exec system process: {}", command
        //this seems the best way to get paths set properly (login shell invocation)
        return ExecUtils.execBlocking("/bin/bash", "-cl", command);
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

    public void startInLocation(Collection<? extends Location> locations) {
        Preconditions.checkArgument locations.size() == 1
        Location location = Iterables.getOnlyElement(locations)
        startInLocation(location)
    }

    public void restart() {
        // TODO better way?
        stop()
        start()
    }
    public void stop() {
        //TODO should stop running irrespective of whether to deploy?
        destroy()
    }
    
    public void startInLocation(OpenshiftLocation ol) {
        addLocations([ol])
        
        String war = getConfig(ROOT_WAR);
        if (!war) throw new IllegalStateException("A WAR file is required to start ${this}")
                
        OpenshiftExpressApplicationAccess osa = new OpenshiftExpressApplicationAccess(username:ol.username, password:ol.password, appName:getAppName())
        //check status of app
        def user = osa.getUserInfo().getData();
        def userInfo = user.user_info
        def appInfo = user.app_info[getAppName()];
        if (appInfo==null) {
            //create app if necessary
            log.debug "{} creating app {}", this, getAppName()
            shouldDestroy = true;
            osa.create(retries: 3);
            
            user = osa.getUserInfo().getData();
            appInfo = user.app_info[getAppName()];
        }
        
        //checkout app
        String server = getAppName()+"-"+userInfo.namespace+"."+userInfo.rhc_domain;
        String gitUrl = "ssh://"+appInfo.uuid+"@"+server+"/~/git/"+getAppName()+".git/"
        String openshiftDir = AbstractSoftwareProcessSshDriver.BROOKLYN_HOME_DIR+"/"+application.id+"/openshift-"+id;
        String openshiftGitDir = openshiftDir + "/git";
        
        log.debug "{} gitting app {}, ", this, getAppName()
        //modify to have WAR file there, commit and push
        //(waiting for dns to propagate first)
        int code = execScriptBlocking("""
mkdir -p ${openshiftGitDir}
cd ${openshiftGitDir}
echo `date` checking for DNS for ${server}
RETRIES_LEFT=10
while [ \$RETRIES_LEFT -gt 0 ]; do { ping -c 1 -t 2 ${server} > /dev/null && break; } || { let RETRIES_LEFT-=1; echo waiting to retry DNS for ${server}; sleep 1; } done
if [ ! \$RETRIES_LEFT -gt 0 ]; then echo "WARNING: timeout contacting server ${server}, OpenShift DNS probably failed to propagate"; [[ -z 'failed' ]]; fi
echo `date` found dns ${server}
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
/*
mkdir -p /tmp/brooklyn/ba108de6-359e-402b-be88-8d4c6ed8ef1c/openshift-4a3eb443-dc4b-4753-8ac2-93547709bbc7/git
cd /tmp/brooklyn/ba108de6-359e-402b-be88-8d4c6ed8ef1c/openshift-4a3eb443-dc4b-4753-8ac2-93547709bbc7/git
Brooklyn4a3eb443-brooklyn.rhcloud.com
git clone ssh://fcd1e24fd2914843a5df9e1ba225a3e6@Brooklyn4a3eb443-brooklyn.rhcloud.com/~/git/Brooklyn4a3eb443.git/
cd Brooklyn4a3eb443
git rm -rf *
mkdir deployments
cp /Users/alex/data/cloudsoft/projects/monterey/gits/brooklyn/extras/openshift/target/test-classes/hello-world.war deployments/ROOT.war
touch deployments/ROOT.war.dodeploy
git add deployments
git commit -m "brooklyn automated project restructure for deployment of WAR file"
git push

 */
        //should now (or soon) be running
        if (code!=0) throw new IllegalStateException("Failed to deploy ${this} app ${getAppName()} (${war}): code ${code}")
        
        log.info "{} app launched: {}", this, getAppName()

        //add support for DynamicWebAppCluster.startInLocation(Openshift)
    }

    //TODO should be a sensor, namespace should be discovered, and URL
    public String getWebAppAddress() {
        return "http://${getAppName()}-brooklyn.rhcloud.com/"
    }
    
    public void destroy() {
        //FIXME should use sensor for whether post-execution destruction is desired
        if (shouldDestroy) {
            log.info "{} destroying app {}", this, getAppName()
            OpenshiftLocation ol = Iterables.getOnlyElement(locations)
            OpenshiftExpressApplicationAccess osa = new OpenshiftExpressApplicationAccess(username:ol.username, password:ol.password, appName:getAppName());
            osa.destroy();
        }
    }
    
}

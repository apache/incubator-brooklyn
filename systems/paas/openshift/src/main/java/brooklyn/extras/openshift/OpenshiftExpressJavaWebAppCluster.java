package brooklyn.extras.openshift;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.extras.openshift.OpenshiftExpressAccess.AppInfoFields;
import brooklyn.extras.openshift.OpenshiftExpressAccess.UserInfoFields;
import brooklyn.extras.openshift.OpenshiftExpressAccess.UserInfoResult;
import brooklyn.location.Location;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.internal.duplicates.ExecUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

class OpenshiftExpressJavaWebAppCluster extends AbstractEntity implements Startable, JavaWebAppService {

    // TODO Not tested since converted to Java; would not have compiled/run as groovy if groovy was stricter 
    // about unknown refs.
    
    private static final Logger log = LoggerFactory.getLogger(OpenshiftExpressJavaWebAppCluster.class);
    
    public static final ConfigKey<String> APP_NAME = ConfigKeys.newStringConfigKey("appName", "System name for uniquely referring to application; defaults to Brooklyn999999");

    public OpenshiftExpressJavaWebAppCluster() {
        this(MutableMap.of(), null);
    }

    public OpenshiftExpressJavaWebAppCluster(Map flags) {
        this(flags, null);
    }
    
    public OpenshiftExpressJavaWebAppCluster(Entity parent) {
        this(MutableMap.of(), parent);
    }
    
    public OpenshiftExpressJavaWebAppCluster(Map flags, Entity parent) {
        super(flags, parent);
        setConfigIfValNonNull(ROOT_WAR, flags.get("war"));
        setConfigIfValNonNull(APP_NAME, flags.get("appName"));
    }

    public String getAppName() {
        String appName = getConfig(APP_NAME);
        if (appName != null) return appName;
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
        for (String it : script.trim().split("\n")) {
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
                command += " }"; 
            }
        }
        log.info("exec system process: {}", command);
        //this seems the best way to get paths set properly (login shell invocation)
        try {
            return ExecUtils.execBlocking("/bin/bash", "-cl", command);
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
    }
    
    boolean shouldDestroy = false;

    @Override
    public void start(Collection<? extends Location> locations) {
//        setAttribute(SERVICE_STATE, Lifecycle.STARTING)
//        attributePoller = new AttributePoller(this)
//        
//        preStart()
        startInLocation(locations);
//        setAttribute(SERVICE_STATE, Lifecycle.STARTED)
//
//        initSensors()
//        postStart()
//
//        setAttribute(SERVICE_STATE, Lifecycle.RUNNING)
    }

    public void startInLocation(Collection<? extends Location> locations) {
        Preconditions.checkArgument(locations.size() == 1, "must be one location but given %s", locations);
        Location location = Iterables.getOnlyElement(locations);
        startInLocation((OpenshiftLocation)location);
    }

    @Override
    public void restart() {
        // TODO better way?
        stop();
        start(getLocations());
    }
    
    @Override
    public void stop() {
        //TODO should stop running irrespective of whether to deploy?
        destroy();
    }
    
    public void startInLocation(OpenshiftLocation ol) {
        addLocations(ImmutableList.of(ol));
        
        String war = getConfig(ROOT_WAR);
        if (war == null) throw new IllegalStateException("A WAR file is required to start "+this);
                
        OpenshiftExpressApplicationAccess osa = new OpenshiftExpressApplicationAccess(MutableMap.of("username", ol.getUsername(), "password", ol.getPassword(), "appName", getAppName()));
        //check status of app
        UserInfoResult user = osa.getUserInfo().getData();
        UserInfoFields userInfo = null; // user.getUser_info(); FIXME What should this have been?! groovy code would not have worked
        AppInfoFields appInfo = user.app_info.get(getAppName());
        if (appInfo==null) {
            //create app if necessary
            log.debug("{} creating app {}", this, getAppName());
            shouldDestroy = true;
            osa.create(MutableMap.of("retries", 3));
            
            user = osa.getUserInfo().getData();
            appInfo = user.app_info.get(getAppName());
        }
        
        //checkout app
        String server = getAppName()+"-"+userInfo.namespace+"."+userInfo.rhc_domain;
        String gitUrl = "ssh://"+appInfo.uuid+"@"+server+"/~/git/"+getAppName()+".git/";
        String openshiftDir = AbstractSoftwareProcessSshDriver.BROOKLYN_HOME_DIR+"/"+getApplicationId()+"/openshift-"+getId();
        String openshiftGitDir = openshiftDir + "/git";
        
        log.debug("{} gitting app {}, ", this, getAppName());
        //modify to have WAR file there, commit and push
        //(waiting for dns to propagate first)
        int code = execScriptBlocking(
                "mkdir -p "+openshiftGitDir+"\n"+
                "cd "+openshiftGitDir+"\n"+
                "echo `date` checking for DNS for "+server+"\n"+
                "RETRIES_LEFT=10"+"\n"+
                "while [ $RETRIES_LEFT -gt 0 ]; do { ping -c 1 -t 2 "+server+" > /dev/null && break; } || { let RETRIES_LEFT-=1; echo waiting to retry DNS for "+server+"; sleep 1; } done"+"\n"+
                "if [ ! $RETRIES_LEFT -gt 0 ]; then echo \"WARNING: timeout contacting server "+server+", OpenShift DNS probably failed to propagate\"; [[ -z 'failed' ]]; fi"+"\n"+
                "echo `date` found dns "+server+"\n"+
                "git clone "+gitUrl+"\n"+
                "cd "+getAppName()+"\n"+
                "git rm -rf *"+"\n"+
                "mkdir deployments"+"\n"+
                "cp "+war+" deployments/ROOT.war"+"\n"+
                "touch deployments/ROOT.war.dodeploy"+"\n"+
                "git add deployments"+"\n"+
                "git commit -m \"brooklyn automated project restructure for deployment of WAR file\""+"\n"+
                "git push"
                );
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
        if (code!=0) throw new IllegalStateException("Failed to deploy "+this+" app "+getAppName()+" ("+war+"): code "+code);
        
        log.info("{} app launched: {}", this, getAppName());

        //add support for DynamicWebAppCluster.startInLocation(Openshift)
    }

    //TODO should be a sensor, namespace should be discovered, and URL
    public String getWebAppAddress() {
        return String.format("http://%s-brooklyn.rhcloud.com/", getAppName());
    }
    
    public void destroy() {
        //FIXME should use sensor for whether post-execution destruction is desired
        if (shouldDestroy) {
            log.info("{} destroying app {}", this, getAppName());
            OpenshiftLocation ol = (OpenshiftLocation) Iterables.getOnlyElement(getLocations());
            OpenshiftExpressApplicationAccess osa = new OpenshiftExpressApplicationAccess(MutableMap.of("username", ol.getUsername(), "password", ol.getPassword(), "appName", getAppName()));
            osa.destroy();
        }
    }
    
}

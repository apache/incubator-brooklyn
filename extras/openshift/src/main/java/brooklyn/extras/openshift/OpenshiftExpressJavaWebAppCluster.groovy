package brooklyn.extras.openshift

import brooklyn.event.basic.BasicConfigKey;
import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractService
import brooklyn.event.basic.BasicConfigKey
import brooklyn.extras.openshift.OpenshiftExpressAccess.OpenshiftExpressApplicationAccess;
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.util.SshBasedAppSetup;
import brooklyn.util.internal.duplicates.ExecUtils;

class OpenshiftExpressJavaWebAppCluster extends AbstractEntity {

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
        return ExecUtils.execBlocking("/bin/bash", "-c", script.trim().split("\n").join(" && "));
    }
    
    public void startInLocation(OpenshiftLocation ol) {
        String war = getConfig(JavaWebApp.WAR);
        if (!war) throw new IllegalStateException("A WAR file is required to start ${this}")
                
        OpenshiftExpressApplicationAccess osa = new OpenshiftExpressApplicationAccess(username:ol.username, password:ol.password, appName:getAppName())
        //check status of app
        def user = osa.getUserInfo().getData();
        def appInfo = user.app_info[getAppName()];
        if (appInfo==null) {
            //create app if necessary
            println "LOG creating app "+getAppName()
            osa.create();
            
            user = osa.getUserInfo().getData();
            appInfo = user.app_info[getAppName()];
        }
        
        //checkout app
        String gitUrl = "ssh://"+appInfo.uuid+"@"+getAppName()+"-"+user.namespace+"."+user.rhc_domain+"/~/git/"+getAppName()+".git/"
        String openshiftDir = SshBasedAppSetup.BROOKLYN_HOME_DIR+"/"+application.id+"/openshift-"+id;
        String openshiftGitDir = openshiftDir + "/git";
        
        println "LOG gitting app "+getAppName()
        //modify to have WAR file there
        //commit and push
        int code = execScriptBlocking("""
mkdir -p ${openshiftGitDir}
cd ${openshiftGitDir}
git clone ${gitUrl}
cd ${getAppName()}
git rm -rf *
mkdir deployments
cp WAR deployments/ROOT.war
touch deployments/ROOT.war.dodeploy
git add deployments
git commit -m "brooklyn automated project restructure for deployment of WAR file"
git push
""");
        //should now (or soon) be running
        if (code!=0) throw new IllegalStateException("Failed to deploy ${this} app ${getAppName()} (${war})")
        
        println "LOG app launched "+getAppName()

        //add support for DynamicWebAppCluster.startInLocation(Openshift)
    }
    
}

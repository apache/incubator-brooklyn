package brooklyn.entity.webapp.jboss;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.lifecycle.CommonCommands;
import brooklyn.entity.webapp.JavaWebAppSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.NetworkUtils;
import brooklyn.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static brooklyn.util.StringUtils.isEmpty;
import static java.lang.String.format;

public class JBoss6SshDriver extends JavaWebAppSshDriver implements JBoss6Driver {

    public static final String SERVER_TYPE = "standard";
    public static final int DEFAULT_HTTP_PORT = 8080;
    private static final String PORT_GROUP_NAME = "ports-brooklyn";

    public JBoss6SshDriver(JBoss6Server entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    protected String getLogFileLocation() {
        return format("%s/server/%s/log/server.log",getRunDir(),SERVER_TYPE);
    }

    protected String getDeploySubdir() {
        return format("server/%s/deploy", SERVER_TYPE);
    } // FIXME what is this in as6?

    protected Integer getPortIncrement() {
        return entity.getAttribute(JBoss6Server.PORT_INCREMENT);
    }

    protected String getClusterName() {
        return entity.getAttribute(JBoss6Server.CLUSTER_NAME);
    }

    @Override
    public void postLaunch() {
       entity.setAttribute(JBoss6Server.HTTP_PORT, DEFAULT_HTTP_PORT + getPortIncrement());
        super.postLaunch();
    }

    @Override
    public void install() {
        String url = "http://downloads.sourceforge.net/project/jboss/JBoss/JBoss-"+getVersion()+"/jboss-as-distribution-"+getVersion()+".zip?" +
                "r=http%3A%2F%2Fsourceforge.net%2Fprojects%2Fjboss%2Ffiles%2FJBoss%2F"+getVersion()+"%2F&ts=1307104229&use_mirror=kent";
        String saveAs = format("jboss-as-distribution-%s.tar.gz", getVersion());
        // Note the -o option to unzip, to overwrite existing files without warning.
        // The JBoss zip file contains lgpl.txt (at least) twice and the prompt to
        // overwrite interrupts the installer.

        List<String> commands = new LinkedList<String>();
        commands.addAll(CommonCommands.downloadUrlAs(url, getEntityVersionLabel("/"), saveAs));
        commands.add(CommonCommands.installExecutable("unzip"));
        commands.add(format("unzip -o %s",saveAs));

        newScript(INSTALLING).
                failOnNonZeroResultCode().
                body.append(commands).execute();
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING).
                body.append(
                format("mkdir -p %s/server", getRunDir()),
                format("cd %s/server", getRunDir()),
                format("cp -r %s/jboss-%s/server/%s %s", getInstallDir(), getVersion(), SERVER_TYPE, SERVER_TYPE),
                format("cd %s/conf/bindingservice.beans/META-INF/",SERVER_TYPE),
                "BJB=\"bindings-jboss-beans.xml\"",
                format("sed -i.bk 's/ports-03/%s/' $BJB",PORT_GROUP_NAME),
                format("sed -i.bk 's/<parameter>300<\\/parameter>/<parameter>%s<\\/parameter>/' $BJB",getPortIncrement())
                ).execute();

        ((JBoss6Server)entity).deployInitialWars();
    }

    @Override
    public void launch() {
        Map<String,Integer> ports = new HashMap<String, Integer>();
        ports.put("httpPort",getHttpPort());
        ports.put("jmxPort",getJmxPort());

        NetworkUtils.checkPortsValid(ports);

        String clusterArg = isEmpty(getClusterName()) ? "":"-g "+getClusterName();
        // run.sh must be backgrounded otherwise the script will never return.

        Map<String,Object> flags = new HashMap<String, Object>();
        flags.put("usePidFile",false);

        newScript(flags, LAUNCHING).
            body.append(
                format("export JBOSS_CLASSPATH=%s/jboss-%s/lib/jboss-logmanager.jar",getInstallDir(),getVersion()),
                format("%s/jboss-%s/bin/run.sh -Djboss.service.binding.set=%s -Djboss.server.base.dir=$RUN_DIR/server ",getInstallDir(),getVersion(),PORT_GROUP_NAME) +
                format("-Djboss.server.base.url=file://$RUN_DIR/server -Djboss.messaging.ServerPeerID=%s ",entity.getId())+
                format("-Djboss.boot.server.log.dir=%s/server/%s/log ",getRunDir(),SERVER_TYPE) +
                format("-b 0.0.0.0 %s -c %s ",clusterArg,SERVER_TYPE) +
                ">>$RUN_DIR/console 2>&1 </dev/null &"
        ).execute();
    }

    @Override
    public boolean isRunning() {
        String host = entity.getAttribute(Attributes.HOSTNAME);
        Integer port = entity.getAttribute(Attributes.JMX_PORT);

        List<String> checkRunningScript = new LinkedList<String>();
        checkRunningScript.add(
                format("%s/jboss-%s/bin/twiddle.sh --host %s --port %s get \"jboss.system:type=Server\" Started | grep false && exit 1",
                        getInstallDir(), getVersion(), host, port));

        //have to override the CLI/JMX options

        Map<String, Object> flags = new LinkedHashMap<String, Object>();
        flags.put("env", new LinkedHashMap<String, String>());

        int result = execute(flags, checkRunningScript, "checkRunning " + entity + " on " + getMachine());
        if (result == 0) return true;
        if (result == 1) return false;
        throw new IllegalStateException(format("%s running check gave result code %s",getEntity(),result));
    }

    @Override
    public void stop() {
        String host = entity.getAttribute(Attributes.HOSTNAME);
        Integer port = entity.getAttribute(Attributes.JMX_PORT);
        List<String> shutdownScript = new LinkedList<String>();
        shutdownScript.add(format("%s/jboss-%s/bin/shutdown.sh --host %s --port %s -S", getInstallDir(), getVersion(), host, port));

        //again, messy copy of parent; but new driver scheme could allow script-helper to customise parameters
        log.debug("invoking shutdown script for {}: {}", entity, shutdownScript);
        Map<String, Object> flags = new LinkedHashMap<String, Object>();
        flags.put("env", new LinkedHashMap<String, String>());
        int result = execute(flags, shutdownScript, "shutdown " + entity + " on " + getMachine());
        if (result != 0) log.warn("non-zero result code terminating {}: {}", entity, result);
        log.debug("done invoking shutdown script for {}", entity);
    }

    @Override
    public void kill() {
        stop(); // TODO No pid file to easily do a `kill -9`
    }
        
    @Override
    protected List<String> getCustomJavaConfigOptions() {
        List<String> options = new LinkedList<String>();
        options.addAll(super.getCustomJavaConfigOptions());
        options.add("-Xms200m");
        options.add("-Xmx800m");
        options.add("-XX:MaxPermSize=400m");
        return options;
    }

    @Override
    public Map<String, String> getShellEnvironment() {
        Map<String, String> env = new LinkedHashMap<String, String>();
        env.putAll(super.getShellEnvironment());
        env.put("LAUNCH_JBOSS_IN_BACKGROUND", "1");
        env.put("RUN", getRunDir());
        return env;
    }

    @Override
    protected Map getCustomJavaSystemProperties() {
        Map<String, String> options = new LinkedHashMap<String, String>();
        options.put("jboss.platform.mbeanserver", null);
        options.put("javax.management.builder.initial", "org.jboss.system.server.jmx.MBeanServerBuilderImpl");
        options.put("java.util.logging.manager", "org.jboss.logmanager.LogManager");
        options.put("org.jboss.logging.Logger.pluginClass", "org.jboss.logging.logmanager.LoggerPluginImpl");
        return options;
    }
}

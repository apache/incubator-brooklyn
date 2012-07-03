package brooklyn.entity.java;

import brooklyn.entity.basic.lifecycle.JavaStartStopSshDriver;
import brooklyn.location.MachineLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.ResourceUtils;
import brooklyn.util.internal.StringEscapeUtils;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;

public class VanillaJavaAppSshDriver extends JavaStartStopSshDriver {

    public VanillaJavaAppSshDriver(VanillaJavaApp entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    public VanillaJavaApp getEntity() {
        return (VanillaJavaApp) super.getEntity();
    }

    public boolean isJmxEnabled() {
        return super.isJmxEnabled() && getEntity().useJmx;
    }

    protected String getLogFileLocation() {
        return format("%s/console",getRunDir());
    }

    @Override
    public void install() {
        newScript(INSTALLING).
            failOnNonZeroResultCode().
            execute();
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING).
            failOnNonZeroResultCode().
            body.append(format("mkdir -p %s/lib",getRunDir())).
            execute();

        ResourceUtils r = new ResourceUtils(entity);
        SshMachineLocation machine = getMachine();
        VanillaJavaApp entity = getEntity();
        for (String f: entity.classpath) {
            // TODO if it's a local folder then JAR it up before sending?
            // TODO support wildcards
            int result = machine.installTo(new ResourceUtils(entity), f, getRunDir()+"/"+"lib"+"/");
            if (result!=0)
                throw new IllegalStateException("unable to install classpath entry $f for $entity at $machine");
            // if it's a zip or tgz then expand

            // FIXME dedup with code in machine.installTo above
            String destName = f;
            destName = destName.contains("?") ? destName.substring(0, destName.indexOf('?')) : destName;
            destName = destName.substring(destName.lastIndexOf('/') + 1);

            if (destName.toLowerCase().endsWith(".zip")) {
                result = machine.run("cd $runDir/lib && unzip $destName");
            } else if (destName.toLowerCase().endsWith(".tgz") || destName.toLowerCase().endsWith(".tar.gz")) {
                result = machine.run("cd $runDir/lib && tar xvfz $destName");
            } else if (destName.toLowerCase().endsWith(".tar")) {
                result = machine.run("cd $runDir/lib && tar xvfz $destName");
            }
            if (result!=0)
                throw new IllegalStateException("unable to install classpath entry $f for $entity at $machine (failed to expand archive)");
        }
    }

    @Override
    public void launch() {
        VanillaJavaApp entity = getEntity();

        String clazz = entity.getMainClass();
        String args = entity.getConfig(VanillaJavaApp.ARGS).collect({
            StringEscapeUtils.assertValidForDoubleQuotingInBash(it);
            return "\""+it+"\"";
        }).join(" ");

        newScript(LAUNCHING, usePidFile:true).
            body.append(
                "echo \"launching: java \$JAVA_OPTS -cp \'lib/*\' $clazz $args\"",
                "java \$JAVA_OPTS -cp \"lib/*\" $clazz $args "+
                    " >> $runDir/console 2>&1 </dev/null &",
            ).execute();
    }

    @Override
    public boolean isRunning() {
        Map flags = new HashMap();
        flags.put("usePidFile",true);

        int result = newScript(flags, CHECK_RUNNING).execute();
        return result == 0;
    }

    @Override
    public void stop() {
        Map flags = new HashMap();
        flags.put("usePidFile",true);

        newScript(flags, STOPPING).execute();
    }

    @Override
    protected Map getCustomJavaSystemProperties() {
        VanillaJavaApp entity  = getEntity();
        Map result = new HashMap();
        result.putAll(super.getCustomJavaSystemProperties());
        result.putAll(entity.getJvmDefines());
        return result;
    }

    @Override
    protected List<String> getCustomJavaConfigOptions() {
        VanillaJavaApp entity  = getEntity();

        List<String> result = new LinkedList<String>();
        result.addAll(super.getCustomJavaConfigOptions());
        result.addAll(entity.getJvmXArgs());
        return result;
    }
}

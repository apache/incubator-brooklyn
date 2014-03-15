package brooklyn.entity.java;

import static java.lang.String.format;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.file.ArchiveBuilder;
import brooklyn.util.file.ArchiveUtils;
import brooklyn.util.net.Urls;
import brooklyn.util.os.Os;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.StringEscapes.BashStringEscapes;

/**
 * The SSH implementation of the {@link VanillaJavaAppDriver}.
 */
public class VanillaJavaAppSshDriver extends JavaSoftwareProcessSshDriver implements VanillaJavaAppDriver {

    // FIXME this should be a config, either on the entity or -- probably better -- 
    // an alternative / override timeout on the SshTool for file copy commands
    final static int NUM_RETRIES_FOR_COPYING = 4;
    
    public VanillaJavaAppSshDriver(VanillaJavaAppImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    public VanillaJavaAppImpl getEntity() {
        return (VanillaJavaAppImpl) super.getEntity();
    }

    protected String getLogFileLocation() {
        return format("%s/console", getRunDir());
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
                body.append(format("mkdir -p %s/lib", getRunDir())).
                execute();

        SshMachineLocation machine = getMachine();
        VanillaJavaApp entity = getEntity();
        for (String entry : entity.getClasspath()) {
            // If a local folder, then create archive from contents first
            if (Urls.isDirectory(entry)) {
                File jarFile = ArchiveBuilder.jar().add(entry).create();
                entry = jarFile.getAbsolutePath();
            }

            // Determine filename
            String destFile = entry.contains("?") ? entry.substring(0, entry.indexOf('?')) : entry;
            destFile = destFile.substring(destFile.lastIndexOf('/') + 1);

            ArchiveUtils.deploy(MutableMap.<String, Object>of(), entry, machine, getRunDir(), Os.mergePaths(getRunDir(), "lib"), destFile);
        }
    }

    @Override
    public void launch() {
        VanillaJavaApp entity = getEntity();

        String clazz = entity.getMainClass();
        String args = getArgs();

        Map flags = new HashMap();
        flags.put("usePidFile", true);

        newScript(flags, LAUNCHING).
            body.append(
                format("echo \"launching: java $JAVA_OPTS -cp \'lib/*\' %s %s\"",clazz,args),
                format("java $JAVA_OPTS -cp \"lib/*\" %s %s >> %s/console 2>&1 </dev/null &",clazz, args, getRunDir())
        ).execute();
    }

    public String getArgs(){
        List<Object> args = entity.getConfig(VanillaJavaApp.ARGS);
        StringBuilder sb = new StringBuilder();
        for(Iterator<Object> it = args.iterator();it.hasNext();){
            Object argO = it.next();
            String arg;
            try {
                arg = Tasks.resolveValue(argO, String.class, getEntity().getExecutionContext());
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            }
            BashStringEscapes.assertValidForDoubleQuotingInBash(arg);
            sb.append(format("\"%s\"",arg));
            if(it.hasNext()){
                sb.append(" ");
            }
        }

        return sb.toString();
    }

    @Override
    public boolean isRunning() {
        Map flags = new HashMap();
        flags.put("usePidFile", true);
        int result = newScript(flags, CHECK_RUNNING).execute();
        return result == 0;
    }

    @Override
    public void stop() {
        Map flags = new HashMap();
        flags.put("usePidFile", true);
        newScript(flags, STOPPING).execute();
    }

    @Override
    public void kill() {
        newScript(MutableMap.of("usePidFile", true), KILLING).execute();
    }

    @Override
    protected Map getCustomJavaSystemProperties() {
        VanillaJavaApp entity = getEntity();
        Map result = new HashMap();
        result.putAll(super.getCustomJavaSystemProperties());
        result.putAll(entity.getJvmDefines());
        return result;
    }

    @Override
    protected List<String> getCustomJavaConfigOptions() {
        VanillaJavaApp entity = getEntity();

        List<String> result = new LinkedList<String>();
        result.addAll(super.getCustomJavaConfigOptions());
        result.addAll(entity.getJvmXArgs());
        return result;
    }
}

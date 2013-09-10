package brooklyn.entity.java;

import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.StringEscapes.BashStringEscapes;

/**
 * The SSH implementation of the {@link VanillaJavaAppDriver}.
 */
public class VanillaJavaAppSshDriver extends JavaSoftwareProcessSshDriver implements VanillaJavaAppDriver {

    public VanillaJavaAppSshDriver(VanillaJavaApp entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    public VanillaJavaApp getEntity() {
        return (VanillaJavaApp) super.getEntity();
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

        ResourceUtils r = new ResourceUtils(entity);
        SshMachineLocation machine = getMachine();
        VanillaJavaApp entity = getEntity();
        for (String f : entity.getClasspath()) {
            // TODO support wildcards

            // If a local folder, then jar it up
            String toinstall;
            if (new File(f).isDirectory()) {
                try {
                    File jarFile = JarBuilder.buildJar(new File(f));
                    toinstall = jarFile.getAbsolutePath();
                } catch (IOException e) {
                    throw new IllegalStateException("Error jarring classpath entry, for directory "+f, e);
                }
            } else {
                toinstall = f;
            }
            
            int result = machine.installTo(new ResourceUtils(entity), toinstall, getRunDir() + "/" + "lib" + "/");
            if (result != 0)
                throw new IllegalStateException(format("unable to install classpath entry %s for %s at %s",f,entity,machine));
            
            // if it's a zip or tgz then expand
            // FIXME dedup with code in machine.installTo above
            String destName = f;
            destName = destName.contains("?") ? destName.substring(0, destName.indexOf('?')) : destName;
            destName = destName.substring(destName.lastIndexOf('/') + 1);

            if (destName.toLowerCase().endsWith(".zip")) {
                result = machine.execCommands("unzipping", ImmutableList.of(format("cd %s/lib && unzip %s",getRunDir(),destName)));
            } else if (destName.toLowerCase().endsWith(".tgz") || destName.toLowerCase().endsWith(".tar.gz")) {
                result = machine.execCommands("untarring gz", ImmutableList.of(format("cd %s/lib && tar xvfz %s",getRunDir(),destName)));
            } else if (destName.toLowerCase().endsWith(".tar")) {
                result = machine.execCommands("untarring", ImmutableList.of(format("cd %s/lib && tar xvfz %s",getRunDir(),destName)));
            }
            if (result != 0)
                throw new IllegalStateException(format("unable to install classpath entry %s for %s at %s (failed to expand archive)",f,entity,machine));
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
        List<String> args = entity.getConfig(VanillaJavaApp.ARGS);
        StringBuffer sb = new StringBuffer();
        for(Iterator<String> it = args.iterator();it.hasNext();){
            String arg = it.next();
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

package brooklyn.entity.nosql.riak;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.lifecycle.ScriptHelper;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.net.Urls;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.DynamicTasks;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static brooklyn.util.ssh.BashCommands.*;
import static java.lang.String.format;

// TODO: Alter -env ERL_CRASH_DUMP path in vm.args
public class RiakNodeSshDriver extends AbstractSoftwareProcessSshDriver implements RiakNodeDriver {

    private static final Logger LOG = LoggerFactory.getLogger(RiakNodeSshDriver.class);
    private boolean isPackageInstall = false;
    private boolean isRiakOnPath = true;

    private static final String sbinPath = "$PATH:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin";

    public RiakNodeSshDriver(final RiakNodeImpl entity, final SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public RiakNodeImpl getEntity() {
        return RiakNodeImpl.class.cast(super.getEntity());
    }

    @Override
    public void install() {
        DownloadResolver resolver = Entities.newDownloader(this);
        String saveAs = resolver.getFilename();
        String expandedInstallDir = getInstallDir() + "/" + resolver.getUnpackedDirectoryName(format("riak-%s", getVersion()));
        setExpandedInstallDir(expandedInstallDir);

        OsDetails osDetails = getMachine().getMachineDetails().getOsDetails();
        List<String> commands = Lists.newLinkedList();
        if (osDetails.isLinux()) {
            commands.addAll(installLinux(expandedInstallDir));
        } else if (osDetails.isMac()) {
            commands.addAll(installMac(saveAs));
        } else if (osDetails.isWindows()) {
            throw new UnsupportedOperationException("RiakNode not supported on Windows instances");
        } else {
            throw new IllegalStateException("Machine was not detected as linux, mac or windows! Installation does not know how to proceed with " +
                    getMachine() + ". Details: " + getMachine().getMachineDetails().getOsDetails());
        }
        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands)
                .execute();

    }

    private List<String> installLinux(String expandedInstallDir) {
        LOG.info("Ignoring version config ({}) and installing from package manager", getEntity().getConfig(RiakNode.SUGGESTED_VERSION));
        isPackageInstall = true;
        String installBin = Urls.mergePaths(expandedInstallDir, "bin");
        String apt = chainGroup(
                //debian fix
                "export PATH=$PATH:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "which apt-get",
                "curl http://apt.basho.com/gpg/basho.apt.key | " + sudo("apt-key add -"),
                sudo("bash -c \"echo deb http://apt.basho.com $(lsb_release -sc) main > /etc/apt/sources.list.d/basho.list\""),
                sudo("apt-get update"),
                sudo("apt-get -y --allow-unauthenticated install riak=" + getEntity().getConfig(RiakNode.SUGGESTED_VERSION) + "*"));
        String yum = chainGroup(
                "which yum",
                sudo("yum -y --nogpgcheck install http://yum.basho.com/gpg/basho-release-5-1.noarch.rpm"),
                sudo("yum -y --nogpgcheck install riak-" + getEntity().getConfig(RiakNode.SUGGESTED_VERSION) + "*"));
        return ImmutableList.<String>builder()
                .add("mkdir -p " + installBin)
                .add(INSTALL_CURL)
                .add(alternatives(apt, yum))
                .add("ln -s `which riak` " + Urls.mergePaths(installBin, "riak"))
                .add("ln -s `which riak-admin` " + Urls.mergePaths(installBin, "riak-admin"))
                .build();
    }

    private List<String> installMac(String saveAs) {
        String fullVersion = getEntity().getConfig(RiakNode.SUGGESTED_VERSION);
        String majorVersion = fullVersion.substring(0, 3);
        // Docs refer to 10.8. No download for 10.9 seems to exist.
        String hostOsVersion = "10.8";
        String url = String.format("http://s3.amazonaws.com/downloads.basho.com/riak/%s/%s/osx/%s/riak-%s-OSX-x86_64.tar.gz",
                majorVersion, fullVersion, hostOsVersion, fullVersion);
        return ImmutableList.<String>builder()
                .add(INSTALL_TAR)
                .add(INSTALL_CURL)
                .add(commandToDownloadUrlAs(url, saveAs))
                .add("tar xzvf " + saveAs)
                .build();
    }

    @Override
    public void customize() {

        isRiakOnPath = isPackageInstall ? isRiakOnPath() : true;

        OsDetails osDetails = getMachine().getMachineDetails().getOsDetails();
        List<String> commands = Lists.newLinkedList();

        String vmArgsTemplate = processTemplate(entity.getConfig(RiakNode.RIAK_VM_ARGS_TEMPLATE_URL));

        //create entity's runDir
        DynamicTasks.queueIfPossible(newScript(CUSTOMIZING).body.append("true").newTask());

        DynamicTasks.queueIfPossible(SshEffectorTasks.put(getRunDir() + "/vm.args")
                .contents(Streams.newInputStreamWithContents(vmArgsTemplate))
                .machine(getMachine())
                .summary("sending the vm.args file to the riak node"));

        //increase open file limit (default min for riak is: 4096)
        //TODO: detect the actual limit then do the modificaiton.
        //TODO: modify ulimit for linux distros
        //    commands.add(sudo("launchctl limit maxfiles 4096 32768"));
        if (osDetails.isMac())
            commands.add("ulimit -n 4096");
        else if (osDetails.isLinux()) {
            commands.add(sudo("chown riak:riak " + getRunDir() + "/vm.args"));
        }

        commands.add(sudo("mv " + getRunDir() + "/vm.args " + getEtcDir()));

        //FIXME EC2 requires to configure the private IP in order for the riak node to work.
        //replace instances of 127.0.0.1 with the actual hostname in the app.config and vm.args files
        commands.add(sudo(format("sed -i -e \"s/127.0.0.1/%s/g\" %s", getPrivateIp(), getAppConfigLocation())));

        ScriptHelper customizeScript = newScript(CUSTOMIZING)
                .failOnNonZeroResultCode()
                .body.append(commands);

        if (!isRiakOnPath) {
            Map<String, String> newPathVariable = ImmutableMap.of("PATH", sbinPath);
            log.warn("riak command not found on PATH. Altering future commands' environment variables from {} to {}", getShellEnvironment(), newPathVariable);
            customizeScript.environmentVariablesReset(newPathVariable);
        }

        customizeScript.execute();

        //set the riak node name
        entity.setAttribute(RiakNode.RIAK_NODE_NAME, format("riak@%s", getHostname()));

    }

    @Override
    public void launch() {

        String command = format("%s start >/dev/null 2>&1 < /dev/null &", getRiakCmd());
        command = isPackageInstall ? "sudo " + command : command;

        ScriptHelper launchScript = newScript(LAUNCHING)
                .failOnNonZeroResultCode()
                .body.append(command);

        if (!isRiakOnPath) {
            Map<String, String> newPathVariable = ImmutableMap.of("PATH", sbinPath);
            log.warn("riak command not found on PATH. Altering future commands' environment variables from {} to {}", getShellEnvironment(), newPathVariable);
            launchScript.environmentVariablesReset(newPathVariable);
        }

        launchScript.execute();
    }

    @Override
    public void stop() {

        if (entity.getAttribute(RiakNode.RIAK_NODE_IN_CLUSTER))
            leaveCluster();

        String command = format("%s stop", getRiakCmd());
        command = isPackageInstall ? "sudo " + command : command;

        ScriptHelper stopScript = newScript(STOPPING)
                .failOnNonZeroResultCode()
                .body.append(command);

        if (!isRiakOnPath) {
            Map<String, String> newPathVariable = ImmutableMap.of("PATH", sbinPath);
            log.warn("riak command not found on PATH. Altering future commands' environment variables from {} to {}", getShellEnvironment(), newPathVariable);
            stopScript.environmentVariablesReset(newPathVariable);
        }

        stopScript.execute();

    }

    @Override
    public boolean isRunning() {

        ScriptHelper checkRunningScript = newScript(CHECK_RUNNING)
                .body.append(format("%s ping", getRiakCmd()));

        if (!isRiakOnPath) {
            Map<String, String> newPathVariable = ImmutableMap.of("PATH", sbinPath);
            log.warn("riak command not found on PATH. Altering future commands' environment variables from {} to {}", getShellEnvironment(), newPathVariable);
            checkRunningScript.environmentVariablesReset(newPathVariable);
        }

        return (checkRunningScript.execute() == 0);

    }

    public String getEtcDir() {
        return isPackageInstall ? "/etc/riak" : Urls.mergePaths(getExpandedInstallDir(), "etc");
    }

    private String getRiakCmd() {
        return isPackageInstall ? "riak" : Urls.mergePaths(getExpandedInstallDir(), "bin/riak");
    }

    private String getRiakAdminCmd() {
        return isPackageInstall ? "riak-admin" : Urls.mergePaths(getExpandedInstallDir(), "bin/riak-admin");
    }

    private String getAppConfigLocation() {
        return Urls.mergePaths(getEtcDir(), "app.config");
    }

    @Override
    public void joinCluster(RiakNode node) {
        //FIXME: find a way to batch commit the changes, instead of committing for every operation.

        if (!isInCluster()) {
            String riakName = node.getAttribute(RiakNode.RIAK_NODE_NAME);

            ScriptHelper joinClusterScript = newScript("joinCluster")
                    .body.append(format("%s cluster join %s", getRiakAdminCmd(), riakName))
                    .body.append(format("%s cluster plan", getRiakAdminCmd()))
                    .body.append(format("%s cluster commit", getRiakAdminCmd()))
                    .failOnNonZeroResultCode();

            if (!isRiakOnPath) {
                Map<String, String> newPathVariable = ImmutableMap.of("PATH", sbinPath);
                log.warn("riak command not found on PATH. Altering future commands' environment variables from {} to {}", getShellEnvironment(), newPathVariable);
                joinClusterScript.environmentVariablesReset(newPathVariable);
            }

            joinClusterScript.execute();


            entity.setAttribute(RiakNode.RIAK_NODE_IN_CLUSTER, Boolean.TRUE);
        } else {
            log.warn("entity {}: is already in the riak cluster", entity.getId());
        }
    }

    @Override
    public void leaveCluster() {
        //TODO: add 'riak-admin cluster force-remove' for erreneous and unrecoverable nodes.
        //FIXME: find a way to batch commit the changes, instead of committing for every operation.

        if (isInCluster()) {
            ScriptHelper leaveClusterScript = newScript("leaveCluster")
                    .body.append(format("%s cluster leave"))
                    .body.append(format("%s cluster plan", getRiakAdminCmd()))
                    .body.append(format("%s cluster commit", getRiakAdminCmd()))
                    .failOnNonZeroResultCode();

            if (!isRiakOnPath) {
                Map<String, String> newPathVariable = ImmutableMap.of("PATH", sbinPath);
                log.warn("riak command not found on PATH. Altering future commands' environment variables from {} to {}", getShellEnvironment(), newPathVariable);
                leaveClusterScript.environmentVariablesReset(newPathVariable);
            }

            leaveClusterScript.execute();


            entity.setAttribute(RiakNode.RIAK_NODE_IN_CLUSTER, Boolean.FALSE);


        } else {
            log.warn("entity {}: is not in the riak Cluster", entity.getId());
        }
    }

    private String getVmArgsLocation() {
        return Urls.mergePaths(getEtcDir(), "vm.args");
    }

    private String getPrivateIp() {
        Optional<String> subnetAddress = Optional.fromNullable(entity.getAttribute(Attributes.SUBNET_ADDRESS));

        if (subnetAddress.isPresent())
            return subnetAddress.get();
        else
            throw new IllegalArgumentException("Subnet address is not set.");
    }

    private Boolean isInCluster() {
        return Optional.fromNullable(entity.getAttribute(RiakNode.RIAK_NODE_IN_CLUSTER)).or(Boolean.FALSE);
    }

    private boolean isRiakOnPath() {
        return (newScript("riakOnPath")
                .body.append("which riak")
                .execute() == 0);
    }


}

package brooklyn.entity.nosql.riak;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.nosql.mongodb.MongoDBServerImpl;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.net.Urls;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.DynamicTasks;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static brooklyn.util.ssh.BashCommands.*;
import static java.lang.String.format;

// TODO: Alter -env ERL_CRASH_DUMP path in vm.args
public class RiakNodeSshDriver extends AbstractSoftwareProcessSshDriver implements RiakNodeDriver {

    private static final Logger LOG = LoggerFactory.getLogger(RiakNodeSshDriver.class);
    private boolean isPackageInstall = false;

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

        OsDetails osDetails = getMachine().getMachineDetails().getOsDetails();
        List<String> commands = Lists.newLinkedList();

        String vmArgsTemplate = processTemplate(entity.getConfig(RiakNode.RIAK_VM_ARGS_TEMPLATE_URL));

        DynamicTasks.queueIfPossible(SshEffectorTasks.put(getVmArgsLocation())
                .contents(Streams.newInputStreamWithContents(vmArgsTemplate))
                .machine(getMachine())
                .summary("sending the vm.args file to the riak node"));

        // Edit file at getAppConfigLocation as per instructions at
        // http://docs.basho.com/riak/2.0.0pre20/ops/building/basic-cluster-setup/
        // Could also set scheduler on linux systems

        //replace instances of 127.0.0.1 with the actual hostname in the app.config file
        commands.add(format("sed -i -e 's/127.0.0.1/%s/g' %s", getHostname(), getAppConfigLocation()));

        //increase open file limit on mac (default min for riak is: 4096)
        //TODO: detect the actual limit then do the modificaiton
        if (osDetails.isMac()) {
            commands.add(BashCommands.sudo("launchctl limit maxfiles 4096 32768"));
            commands.add("ulimit -n 4096");
        }

        newScript(CUSTOMIZING)
                .failOnNonZeroResultCode()
                .body.append(commands)
                .execute();

    }

    @Override
    public void launch() {
        newScript(LAUNCHING)
                .failOnNonZeroResultCode()
                .body.append(String.format("nohup sudo %s/bin/riak start >/dev/null 2>&1 < /dev/null &", getInstallDir()))
                .execute();
    }

    @Override
    public void stop() {
        String command = isPackageInstall ? "sudo " : "" + String.format("%s/bin/riak stop", getInstallDir());
        newScript(STOPPING)
                .failOnNonZeroResultCode()
                .body.append(command)
                .execute();
    }

    @Override
    public boolean isRunning() {
        return newScript("isrunning")
                .body.append(String.format("sudo %s/bin/riak ping", getInstallDir()))
                .execute() == 0;
    }

    public String getEtcDir() {
        return isPackageInstall ? "/etc" : Urls.mergePaths(getExpandedInstallDir(), "etc");
    }

    public String getAppConfigLocation() {
        return Urls.mergePaths(getEtcDir(), "app.config");
    }

    @Override
    public void joinCluster(List<String> clusterHosts) {
        //TODO: use for updating the cluster: bin/riak-admin cluster join riak@192.168.1.10
        //to use after running riak's first node.
    }

    @Override
    public String getVmArgsLocation() {
        return Urls.mergePaths(getEtcDir(), "vm.args");
    }


}

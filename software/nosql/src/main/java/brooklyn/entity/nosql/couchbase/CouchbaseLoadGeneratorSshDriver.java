package brooklyn.entity.nosql.couchbase;

import java.util.List;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableList;

public class CouchbaseLoadGeneratorSshDriver extends AbstractSoftwareProcessSshDriver implements CouchbaseLoadGeneratorDriver {

    public CouchbaseLoadGeneratorSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }
    
    @Override
    public void install() {
        OsDetails osDetails = getMachine().getMachineDetails().getOsDetails();
        if (osDetails.isLinux()) {
            String apt = BashCommands.chainGroup(
                "which apt-get",
                "export DEBIAN_FRONTEND=noninteractive",
                BashCommands.sudo("wget -O/etc/apt/sources.list.d/couchbase.list http://packages.couchbase.com/ubuntu/couchbase-ubuntu1204.list"),
                "wget -O- http://packages.couchbase.com/ubuntu/couchbase.key | sudo apt-key add - ",
                BashCommands.sudo("apt-get update"),
                BashCommands.sudo("apt-get install -y libcouchbase2-libevent libcouchbase-dev libcouchbase2-bin")
            );
            String yum = BashCommands.chainGroup(
                // TODO: 32bit / 64bit
                "which yum",
                BashCommands.sudo("wget -O/etc/yum.repos.d/couchbase.repo http://packages.couchbase.com/rpm/couchbase-centos55-x86_64.repo"),
                BashCommands.ok(BashCommands.sudo("yum check-update")),
                BashCommands.sudo("yum install -y  libcouchbase2-libevent libcouchbase-devel libcouchbase2-bin")
            );
            List<String> commands = ImmutableList.<String>builder()
                    .add(BashCommands.INSTALL_WGET)
                    .add(BashCommands.alternatives(apt, yum))
                    .build();
            newScript(INSTALLING)
                    .body.append(commands).execute();
        }
    }
    
    @Override
    public void customize() {
        // no-op
    }
    
    @Override
    public void launch() {
        // no-op, process is only launch when pillowfight is called
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public void stop() {
        // no-op
    }

    @Override
    public void pillowfight(String targetHostnameAndPort, String bucket, String username, String password, Integer iterations,
            Integer numItems, String keyPrefix, Integer numThreads, Integer numInstances, Integer randomSeed, Integer ratio,
            Integer minSize, Integer maxSize) { 
        StringBuilder builder = new StringBuilder();
        builder.append("cbc pillowfight ");
        addOptionalStringParam(builder, "h", targetHostnameAndPort);
        addOptionalStringParam(builder, "b", bucket);
        addOptionalStringParam(builder, "u", username);
        addOptionalStringParam(builder, "P", password);
        addIntegerParam(builder, "i", iterations);
        addIntegerParam(builder, "I", numItems);
        addOptionalStringParam(builder, "p", keyPrefix);
        addIntegerParam(builder, "t", numThreads);
        addIntegerParam(builder, "Q", numInstances);
        addIntegerParam(builder, "s", randomSeed);
        addIntegerParam(builder, "r", ratio);
        addIntegerParam(builder, "m", minSize);
        addIntegerParam(builder, "M", maxSize);
        newScript("pillow-fight")
            .body.append(builder.toString())
            .gatherOutput()
            .failOnNonZeroResultCode()
            .execute();
    }
    
    private void addOptionalStringParam(StringBuilder builder, String paramFlag, String value) {
        if (!Strings.isEmpty(value)) {
            builder.append(String.format("-%s %s ", paramFlag, value));
        }
    }
    
    private void addIntegerParam(StringBuilder builder, String paramFlag, Integer value) {
        builder.append(String.format("-%s %d ", paramFlag, value));
    }
}

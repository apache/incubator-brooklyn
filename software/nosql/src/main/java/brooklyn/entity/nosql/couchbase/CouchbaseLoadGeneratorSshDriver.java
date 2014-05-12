package brooklyn.entity.nosql.couchbase;

import java.util.List;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.ssh.BashCommands;

import com.google.common.collect.ImmutableList;

public class CouchbaseLoadGeneratorSshDriver extends AbstractSoftwareProcessSshDriver implements CouchbaseLoadGeneratorDriver {

    public CouchbaseLoadGeneratorSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }
    
    @Override
    public void install() {
        OsDetails osDetails = getMachine().getMachineDetails().getOsDetails();
        System.out.print("foo");
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
        // TODO Auto-generated method stub
        
    }
    
    @Override
    public void launch() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public void stop() {
        // TODO Auto-generated method stub

    }

}

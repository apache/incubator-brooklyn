package brooklyn.demo

import java.util.Map

import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.aws.AWSCredentialsFromEnv
import brooklyn.location.basic.aws.AwsLocation
import brooklyn.location.basic.aws.AwsLocationFactory

public class Locations {
    private static final Map MONTEREY_EAST_COORDS = [ 'latitude' : 41.10361d, 'longitude' : -73.79583d ] // Hawthorne, NY
    private static final Map EDINBURGH_COORDS = [ 'latitude' : 55.94944d, 'longitude' : -3.16028d ] // Edinburgh, Scotland
   
    private Locations() { }

    public static AwsLocationFactory newAwsLocationFactory() {
	    // XXX change these paths before running the demo
        File sshPrivateKey = new File("src/main/resources/jclouds/id_rsa.private")
        File sshPublicKey = new File("src/main/resources/jclouds/id_rsa.pub")
        
        AWSCredentialsFromEnv creds = new AWSCredentialsFromEnv();
        return new AwsLocationFactory([
                identity : creds.getAWSAccessKeyId(),
                credential : creds.getAWSSecretKey(),
                sshPrivateKey : sshPrivateKey,
                sshPublicKey : sshPublicKey
            ])
    }

    public static FixedListMachineProvisioningLocation newMontereyEastLocation() {
        // The definition of the Monterey East location
        final Collection<SshMachineLocation> MONTEREY_EAST_PUBLIC_ADDRESSES = [
                '216.48.127.224', '216.48.127.225', // east1a/b
                '216.48.127.226', '216.48.127.227', // east2a/b
                '216.48.127.228', '216.48.127.229', // east3a/b
                '216.48.127.230', '216.48.127.231', // east4a/b
                '216.48.127.232', '216.48.127.233', // east5a/b
                '216.48.127.234', '216.48.127.235'  // east6a/b
            ].collect { new SshMachineLocation(address:InetAddress.getByName(it), userName:'cdm') }

        MachineProvisioningLocation<SshMachineLocation> result =
            new FixedListMachineProvisioningLocation<SshMachineLocation>(
                machines : MONTEREY_EAST_PUBLIC_ADDRESSES,
                latitude : MONTEREY_EAST_COORDS['latitude'],
                longitude : MONTEREY_EAST_COORDS['longitude']
            )
        return result
    }

    public static FixedListMachineProvisioningLocation newMontereyEdinburghLocation() {
        // The definition of the Monterey Edinburgh location
        final Collection<SshMachineLocation> MONTEREY_EDINBURGH_MACHINES = [
                '192.168.144.241',
                '192.168.144.242',
                '192.168.144.243',
                '192.168.144.244',
                '192.168.144.245',
                '192.168.144.246'
            ].collect { new SshMachineLocation(address:InetAddress.getByName(it), userName:'cloudsoft') }

        MachineProvisioningLocation<SshMachineLocation> result =
            new FixedListMachineProvisioningLocation<SshMachineLocation>(
                machines : MONTEREY_EDINBURGH_MACHINES,
                latitude : EDINBURGH_COORDS['latitude'],
                longitude : EDINBURGH_COORDS['longitude']
            )
        return result
    }
}
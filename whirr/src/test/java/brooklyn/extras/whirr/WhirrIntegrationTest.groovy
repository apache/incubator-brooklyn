package brooklyn.extras.whirr;

import brooklyn.entity.basic.AbstractApplication;

public class WhirrIntegrationTest {

    //taken from whirr source, recipse/puppet-http-ec2.properties
    public static final String RECIPE_PUPPET_APACHE = '''
# Change the cluster name here
whirr.cluster-name=puppettest

# Change the number of machines in the cluster here
whirr.instance-templates=1 puppet:apache+puppet:ntp
whirr.firewall-rules=80

# brooklyn sets this
## For EC2 set AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY environment variables.
#whirr.provider=aws-ec2
#whirr.identity=${env:AWS_ACCESS_KEY_ID}
#whirr.credential=${env:AWS_SECRET_ACCESS_KEY}

# with puppet, you need to specify where to pull the modules from
puppet.apache.module=git://github.com/metcalfc/puppet-apache.git
puppet.ntp.module=git://github.com/puppetlabs/puppetlabs-ntp.git

# with puppet, you can set class parameters
ntp.servers=[ '0.pool.ntp.org', 'clock.redhat.com' ]
ntp.autoupdate=true

# By default use the user system SSH keys. Override them here.
# whirr.private-key-file=${sys:user.home}/.ssh/id_rsa
# whirr.public-key-file=${whirr.private-key-file}.pub

# Expert: specify alternate module sources instead of from git
#puppet.http.module=/tmp/git-puppetlabs-http.tgz
'''

    public static class MySampleWhirrApp extends AbstractApplication {
        WhirrCluster whirrCluster = new WhirrCluster(this)
        {
            whirrCluster.loadWhirrConfig(new StringReader(RECIPE_PUPPET_APACHE));
        }
    }
    
    public void testWhirrPuppet() {
        new MySampleWhirrApp().start();
    }
    
}

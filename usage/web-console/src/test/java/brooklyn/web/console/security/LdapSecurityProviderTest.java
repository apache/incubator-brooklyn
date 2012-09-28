package brooklyn.web.console.security;


import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.api.partition.Partition;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertTrue;

public class LdapSecurityProviderTest {

    private DefaultDirectoryService service;

    @BeforeClass
    public void beforeClass()throws Exception{
    // Initialize the LDAP service
        service = new DefaultDirectoryService();

        // Disable the ChangeLog system
        service.getChangeLog().setEnabled( false );

        // Create a new partition named 'apache'.
        //Partition apachePartition = addPartition( "apache", "dc=apache,dc=org" );

        // Index some attributes on the apache partition
        //addIndex( apachePartition, "objectClass", "ou", "uid" );

        // And start the service
        service.startup();

        // Inject the apache root entry if it does not already exist
        //if ( !service.getAdminSession().exists( apachePartition.getSuffixDn() ) )
        //{
        //    LdapDN dnApache = new LdapDN( "dc=Apache,dc=Org" );
        //    ServerEntry entryApache = service.newEntry( dnApache );
        //    entryApache.add( "objectClass", "top", "domain", "extensibleObject" );
        //    entryApache.add( "dc", "Apache" );
        //    service.getAdminSession().add( entryApache );
        //}

    }

    @Test
    public void test() {
        LdapSecurityProvider provider = new LdapSecurityProvider("foo","bar");
        boolean success = provider.authenticate(null,"user","password");
        assertTrue(success);
    }
}

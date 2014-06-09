package brooklyn.entity.chef.mysql;

import brooklyn.entity.chef.ChefConfig;
import brooklyn.entity.chef.ChefEntityImpl;
import brooklyn.util.git.GithubUrls;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/** Illustrates how to define an entity using Java as a Java class, extending ChefEntityImpl */
public class TypedToyMySqlEntityChef extends ChefEntityImpl {

    @Override
    public void init() {
        super.init();

        String password = "p4ssw0rd";
        
        setConfig(CHEF_COOKBOOK_PRIMARY_NAME, "mysql");
        setConfig(CHEF_COOKBOOK_URLS, ImmutableMap.of(
            "mysql", GithubUrls.tgz("opscode-cookbooks", "mysql", "v4.0.12"),
            "openssl", GithubUrls.tgz("opscode-cookbooks", "openssl", "v1.1.0"),
            "mysql", GithubUrls.tgz("opscode-cookbooks", "build-essential", "v1.4.4")));
        
        setConfig(CHEF_LAUNCH_RUN_LIST, ImmutableSet.of("mysql::server"));
        setConfig(CHEF_LAUNCH_ATTRIBUTES, ImmutableMap.<String,Object>of(
            "mysql", ImmutableMap.of(
                "server_root_password", password,
                "server_repl_password", password,
                "server_debian_password", password)));
        
        setConfig(ChefConfig.PID_FILE, "/var/run/mysqld/mysqld.pid");
        
        setConfig(CHEF_MODE, ChefModes.SOLO);
    }

}

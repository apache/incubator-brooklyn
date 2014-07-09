package brooklyn.entity.chef;

import static brooklyn.util.ssh.BashCommands.INSTALL_CURL;
import static brooklyn.util.ssh.BashCommands.INSTALL_TAR;
import static brooklyn.util.ssh.BashCommands.INSTALL_UNZIP;
import static brooklyn.util.ssh.BashCommands.downloadToStdout;
import static brooklyn.util.ssh.BashCommands.sudo;
import brooklyn.util.ssh.BashCommands;

import com.google.common.annotations.Beta;

/** BASH commands useful for setting up Chef */
@Beta
public class ChefBashCommands {

    public static final String INSTALL_FROM_OPSCODE =
            BashCommands.chain(
                    INSTALL_CURL,
                    INSTALL_TAR,
                    INSTALL_UNZIP,
                    "( "+downloadToStdout("https://www.opscode.com/chef/install.sh") + " | " + sudo("bash")+" )");

}

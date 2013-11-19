package brooklyn.entity.database.rubyrep;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.database.DatabaseNode;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;
import com.google.common.base.Functions;

import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class RubyRepNodeImpl extends SoftwareProcessImpl implements RubyRepNode {

    @Override
    protected void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Adds support for binding to brooklyn DatabaseNodes (so the user doesn't have to call attributeWhenReady, etc)
     */
    @Override
    public void init() {
        super.init();

        DatabaseNode leftNode = getConfig(LEFT_DATABASE);
        DatabaseNode rightNode = getConfig(RIGHT_DATABASE);

        if (leftNode != null) {
            setConfig(LEFT_DATABASE_URL, DependentConfiguration.attributeWhenReady(leftNode, DatabaseNode.DATASTORE_URL));
        }
        if (rightNode != null) {
            setConfig(RIGHT_DATABASE_URL, DependentConfiguration.attributeWhenReady(rightNode, DatabaseNode.DATASTORE_URL));
        }
    }

    @Override
    public Class getDriverInterface() {
        return RubyRepDriver.class;
    }

    /**
     * Accessors used in freemarker template processing
     */
    public int getReplicationInterval() {
        return getConfig(REPLICATION_INTERVAL);
    }
    
    public String getTableRegex() {
        return getConfig(TABLE_REGEXP);
    }
    
    public URI getLeftDatabaseUrl() {
        return URI.create(getAttribute(LEFT_DATABASE_URL));
    }
    
    public String getLeftDatabaseName() {
        return getConfig(LEFT_DATABASE_NAME);
    }

    public String getLeftUsername() {
        return getConfig(LEFT_USERNAME);
    }

    public String getLeftPassword() {
        return getConfig(LEFT_PASSWORD);
    }

    public URI getRightDatabaseUrl() {
        return URI.create(getAttribute(RIGHT_DATABASE_URL));
    }

    public String getRightDatabaseName() {
        return getConfig(RIGHT_DATABASE_NAME);
    }

    public String getRightUsername() {
        return getConfig(RIGHT_USERNAME);
    }

    public String getRightPassword() {
        return getConfig(RIGHT_PASSWORD);
    }
}

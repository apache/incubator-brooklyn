
# [![**Brooklyn**](https://brooklyn.apache.org/style/img/apache-brooklyn-logo-244px-wide.png)](http://brooklyn.apache.org/)

### Using Vagrant with Brooklyn -SNAPSHOT releases
Due to the absence of a single source for snapshots (i.e. locally built vs. [periodically published archives](https://brooklyn.apache.org/v/0.9.0-SNAPSHOT/misc/download.html)), we require that you override the supplied `servers.yaml` to explicitly point at your desired `-SNAPSHOT` release.

Full releases use the `BROOKLYN_VERSION` environment variable to download the associated `-bin` artifact from the closest Apache mirror, if this environment variable is set to a `-SNAPSHOT` we abort creating the Brooklyn Vagrant VM.

##### Installing from local file
For example to install from a locally built `-dist` archive:

1. Copy the SNAPSHOT `-dist` archive to the same directory as the `Vagrantfile`

   ```
   cp  ~/Workspaces/incubator-brooklyn/brooklyn-dist/dist/target/brooklyn-dist-0.9.0-SNAPSHOT-dist.tar.gz .
   ```

2. Delete the `BROOKLYN_VERSION:` environment variable from `servers.yaml`. For example:

   ```
   env:
     BROOKLYN_VERSION: 0.9.0-SNAPSHOT
   ```

3. Update `servers.yaml` to install from the `-dist` archive. For example, replace:
   ```
   - curl -s -S -J -O -L "https://www.apache.org/dyn/closer.cgi?action=download&filename=brooklyn/apache-brooklyn-${BROOKLYN_VERSION}/apache-brooklyn-${BROOKLYN_VERSION}-bin.tar.gz"
   - tar zxf apache-brooklyn-${BROOKLYN_VERSION}-bin.tar.gz
   - ln -s apache-brooklyn-${BROOKLYN_VERSION}-bin apache-brooklyn
   ```
   with:
   ```
   - cp /vagrant/brooklyn-dist-0.9.0-SNAPSHOT-dist.tar.gz .
   - tar zxf brooklyn-dist-0.9.0-SNAPSHOT-dist.tar.gz
   - ln -s brooklyn-dist-0.9.0-SNAPSHOT apache-brooklyn
   ```

4. You may proceed to use the `Vagrantfile` as normal; `vagrant up`, `vagrant destroy` etc.

##### Installing from URL
For example to install from a published `-SHAPSHOT` archive:

1. Delete the `BROOKLYN_VERSION:` environment variable from `servers.yaml`. For example:

   ```
   env:
     BROOKLYN_VERSION: 0.9.0-SNAPSHOT
   ```

2. Update `servers.yaml` to install from URL. For example, replace:
   ```
   - curl -s -S -J -O -L "https://www.apache.org/dyn/closer.cgi?action=download&filename=brooklyn/apache-brooklyn-${BROOKLYN_VERSION}/apache-brooklyn-${BROOKLYN_VERSION}-bin.tar.gz"
   - tar zxf apache-brooklyn-${BROOKLYN_VERSION}-bin.tar.gz
   - ln -s apache-brooklyn-${BROOKLYN_VERSION}-bin apache-brooklyn
   ```
   with:
   ```
   - curl -s -S -J -O -L "https://repository.apache.org/service/local/artifact/maven/redirect?r=snapshots&g=org.apache.brooklyn&a=brooklyn-dist&v=0.9.0-SNAPSHOT&c=dist&e=tar.gz"
   - tar zxf brooklyn-dist-0.9.0-SNAPSHOT-dist.tar.gz
   - ln -s brooklyn-dist-0.9.0-SNAPSHOT apache-brooklyn
   ```

3. You may proceed to use the `Vagrantfile` as normal; `vagrant up`, `vagrant destroy` etc.
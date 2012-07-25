## Before You Begin

To use the examples, you'll need ``curl``, ``java``, and ``maven`` (v3) installed.

First, grab a copy of the Brooklyn distribution:

{% highlight bash %}
# download and unpack
curl -L http://developers.cloudsoftcorp.com/maven/releases/io/brooklyn/brooklyn-dist/0.4.0-M2/\
brooklyn-dist-0.4.0-M2-dist.tar.gz | tar xzf -
# set up an environment variable to point to it for convenience
export BROOKLYN_HOME=$(pwd)/brooklyn
{% endhighlight %}

Then, grab a copy of the brooklyn-examples source code and build with Maven:

{% highlight bash %}
# download and unpack
curl -L https://github.com/brooklyncentral/brooklyn-examples/tarball/0.4.0-M2 | tar xzf -
# set up an environment variable to point to it for convenience
export BROOKLYN_EXAMPLES_DIR=$(pwd)/brooklyncentral-brooklyn-examples-b296711
# build with Maven
cd ${BROOKLYN_EXAMPLES_DIR}
mvn clean install
{% endhighlight %}

For more information about where to download brooklyn please
see the [download page]({{site.url}}/start/download.html).

If you wish to learn more about the Brooklyn CLI used for launching an app,
please visit [this section of the user guide]({{site.url}}/use/guide/management/index.html#cli).

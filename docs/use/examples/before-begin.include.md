## Before You Begin

To use the examples, you'll need ``curl``, ``git``, ``java`` (1.6+), and ``maven`` (v3) installed.

{% include fields.md %}

{% if SNAPSHOT %}

First, grab a copy of the Brooklyn snapshot distribution you wish to use from 
[the Sonatype snapshot repo]({{ sonatype_repo_groupid_url }}brooklyn-dist/)
(or build it yourself following instructions [here]({{ site.url }}/dev/build/)),
unpack it to your favourite location (e.g. `$(pwd)`), 
and export `BROOKLYN_HOME`:

{% highlight bash %}
% curl -L -o brooklyn-dist-{{ site.brooklyn-version }}-dist.tar.gz {{ this_dist_tgz_url }}
% tar xvzf brooklyn-dist-{{ site.brooklyn-version }}-dist.tar.gz
% export BROOKLYN_HOME=$(pwd)/brooklyn-{{ site.brooklyn-version }}/
{% endhighlight %}

{% else %}

First, grab a copy of the Brooklyn distribution and set up `BROOKLYN_HOME`:

{% highlight bash %}
% curl -LO {{ this_dist_url_tgz }}
% tar xvzf brooklyn-dist-{{ site.brooklyn-version }}-dist.tar.gz
% export BROOKLYN_HOME=$(pwd)/brooklyn-{{ site.brooklyn-version }}/
{% endhighlight %}

{% endif %}


Then, grab a copy of the brooklyn-examples source code and build with Maven:

{% highlight bash %}
% git clone https://github.com/brooklyncentral/brooklyn-examples.git
% cd brooklyn-examples
{% if brooklyn_examples_branch == 'master' %}{% else %}% git checkout {{ brooklyn_examples_branch }}
{% endif %}% mvn clean install
{% endhighlight %}

{% if SNAPSHOT %}
Please note, these instructions are for a SNAPSHOT release of Brooklyn,
so proceed with caution. 
For the latest stable version, go [here](/meta/versions.html). 
{% endif %}
For more information on ways to download Brooklyn please
see the [download page]({{site.url}}/start/download.html).
For more information on the Brooklyn CLI and launching apps,
please visit [this section of the user guide]({{site.url}}/use/guide/management/index.html#cli).

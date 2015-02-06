
### Operations


{% include feature-item.html title="Brooklyn console" %}

Brooklyn runs with a GUI console giving easy access to the
management hierarchy, sensors, and activities.

{% include feature-item-end.html img="ops-console.png" %}





{% include feature-item.html title="High availability" %}

Run standby nodes which can optionally automatically promote to master
in the event of master failure. Hot standby nodes can provide additional
read-only access to entity information.
{% include feature-item-end.html %}



{% include feature-item.html title="State persistence" %}

Blueprint, catalog, topology and sensor information can be 
automatically persisted to any file system or object store to 
stop Brooklyn and restart resuming where you left off.
{% include feature-item-end.html %}



{% include feature-item.html title="REST API" %}

<p>
The console is pure JS-REST, and all the data shown in the GUI
is available through a straightforward REST/JSON API.
</p>

<p>
In many cases, the REST API is simply the GUI endpoint without the
leading <code>#</code>.  For instance the data for
<code>#/v1/applications/</code> is available at <code>/v1/applications/</code>. 
And in all cases, Swagger doc is available in the product.
</p>
{% include feature-item-end.html img="ops-rest.png" %}



{% include feature-item.html title="Groovy console" %}

With the right permissions, Groovy scripts can be sent via
the GUI or via REST, allowing open-heart surgery on your systems.
(Use with care!) 
{% include feature-item-end.html %}



{% include feature-item.html title="Versioning" %}

Blueprints in the catalog can be versioned on-the-fly.
Running entities are attached to the version against which
they were launched to preserve integrity, until manual
version updates are performed. 
{% include feature-item-end.html %}


{% include feature-item.html title="Deep task information" %}
The console shows task flows in real-time,
including the `stdin` and `stdout` for shell commands,
making it simpler to debug those pesky failures.
{% include feature-item-end.html %}



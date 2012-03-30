## What is Brooklyn?

**brooklyn** is a library that simplifies application deployment and management.

For **deployment**, it is designed to tie in with tools like Whirr, Chef, and Puppet,
as well as platform-as-a-service offerings like OpenShift and CloudFoundry,
or just POBS (plain-old-bash-scripts).
Brooklyn makes roll-out an integral part of the DevOps chain,
as code which can be version-controlled and automatically tested,
and portable across many clouds, fixed IP machines, or even localhost.

Brooklyn's main emphasis however is **management**:
management policies are an integral part of the deployment descriptor,
and at runtime have access to all aspects of the deployment.
They are aware of the deployment topology (hierarchical) and locations (machines, PaaSes, and jurisdictions), 
and also launch mechanisms, monitoring data from managed entities or third-party systems,
and operational goals and constraints, 
so they're all set, once the application is launched, to keep the application running optimally
*based on whatever **optimally** means for you*.

## To Get Started

* See the [developer's walkthrough]({{site.url}}/start/walkthrough/index.html) for a quick tour
* dive in to the [user guide]({{site.url}}/use/guide/) describing the 
  [concepts]({{site.url}}/use/guide/defining-applications/basic-concepts.html)
  and including a [tutorial]({{site.url}}/use/guide/quickstart/).
* or jump straight in to the [code]({{site.url}}/dev/code/), 
  including [examples]({{site.url}}/use/examples/), or other [documentation]({{site.url}}/start/docs-summary.html).

If you like it, join the discussion on the user and developer groups.
[Details]({{site.url}}/meta/contact.html).

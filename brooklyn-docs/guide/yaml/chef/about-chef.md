---
title: About Chef
title_in_menu: About Chef
layout: website-normal
---

## What you need to know about Chef

Chef works in two different modes, *server* and *solo*. *Server* is where the Chef client talks to a central server
to retrieve information about its roles, policies and cookbooks (where a cookbook defines how to install and
configure a particular piece of software). With *solo*, the client works in isolation, therefore its configuration
and cookbooks must be supplied by another means.

Chef *client* is the Chef agent. This is a Ruby application which is installed on each and every managed host. When
invoked in server mode, it will contact the Chef server to check for updates to cookbooks and policy; it then "runs"
the recipes in its run lists, to converge the machine to a known state. In solo mode, it reads the locally-maintained
cookbooks and policies. The client may be run as a daemon that checks the server regularly, or it could merely be
run manually when required.

The *policy* is a set of rules on the Chef server. A client starts with a set of *attributes*, which could be as
simple as its name and a recipe runlist, or which may involve a more complex set of attributes about how it is to be
configured. The client then augments this with auto-detected metadata - a tool called `ohai` is run that collects
detailed information about the host. Next, the policy on the server modifies these attributes - overriding some,
setting defaults for others - to produce a final set of attributes. It is these which are the input to the recipes.
Finally, the attributes are uploaded to the server where they are stored as metadata for the node, where they can be
inspected and modified by the system operator.

Also of interest is `knife`, which is the workstation toolkit for Chef. Typically this would be installed on the
operation engineer's workstation, where it would be used to interact with the Chef server and clients. Of particular
interest to us is the *bootstrap* operation, which is used for setting up new Chef clients - given a virtual machine,
it will install the Chef client on it, configure it with enough information to find the Chef server and performs its
first run, and then kicks off the Chef client for the first time.

There is often a preconception about how a Chef client is bootstrapped; mistakenly, there is the belief that the
`knife` tool configures the Chef server with information about the client, and the client finds out about itself from
the server. This is not the case - the bootstrap operation does not involve `knife` talking to the server. Instead,
`knife` packages up all of the required information and sends it to the client - the client will then introduce
itself to the server, passing on its configuration.

This diagram summarises the interaction between Brooklyn, the new node, and the various Chef tools. Note that there
is no interaction between the AMP Server and the Chef Server.

[![Chef Flow Diagram](chef-call-flow.png "Chef Flow Diagram" )](chef-call-flow.png)

### How Brooklyn interacts with Chef

Brooklyn understands both the *server* and *solo* modes of operation. Server mode utilises the `knife` toolkit, and
therefore `knife` must be installed onto the AMP server and configured appropriately. Solo mode does not have any
special requirements; when running in solo mode, Brooklyn will install and configure the Chef client over SSH, just
like it does most other kinds of entities.

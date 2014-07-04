OASIS CAMP Server
=================

The projects in this directory provide the necessary components for a server which 
speaks the CAMP REST API and which understands the CAMP YAML plan language.

It is not dependent on Brooklyn (apart from utils) and does not expose any
types.  The brooklyn-camp project provides the links for Brooklyn entities
to be available through the CAMP REST API, and for Brooklyn to deploy blueprints
described using the CAMP YAML.

The projects in this directory are designed so they could be used to build
other CAMP servers not based on Brooklyn, if desired.

These projects are part of the Apache Software Foundation Brooklyn project
(brooklyn.io) and released under the Apache License 2.0.


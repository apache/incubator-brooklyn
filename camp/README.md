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

----
Licensed to the Apache Software Foundation (ASF) under one 
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY 
KIND, either express or implied.  See the License for the 
specific language governing permissions and limitations
under the License.
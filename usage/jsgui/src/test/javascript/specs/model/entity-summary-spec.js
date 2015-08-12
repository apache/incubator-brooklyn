/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
define(
    ["model/entity-summary" ],
    function (EntitySummary) {

        describe('model/entity-summary EntitySummary model and collection', function () {
            var summaries = new EntitySummary.Collection
            summaries.url = 'fixtures/entity-summary-list.json'
            summaries.fetch({async:false})
            var eSummary = summaries.at(0)

            it('the collection element must be of type TomcatServer and have expected properties', function () {
                expect(eSummary.getLinkByName('catalog'))
                    .toBe('/v1/catalog/entities/org.apache.brooklyn.entity.webapp.tomcat.TomcatServer')
                expect(eSummary.get("type")).toBe('org.apache.brooklyn.entity.webapp.tomcat.TomcatServer')
                expect(eSummary.getLinkByName('sensors')).toBe('fixtures/sensor-summary-list.json')
                expect(eSummary.getDisplayName()).toBe('TomcatServer:zQsqdXzi')
            })

            it('collection has working findByDisplayName function', function () {
                expect(summaries.findByDisplayName('test').length).toBe(0)
                expect(summaries.findByDisplayName(eSummary.getDisplayName()).length).toBe(1)
                expect(JSON.stringify(summaries.findByDisplayName(eSummary.getDisplayName()).pop().toJSON())).toBe(JSON.stringify(eSummary.toJSON()))
            })

            it('collection must have one element', function () {
                expect(summaries.length).toBe(1)
            })

        })
    })
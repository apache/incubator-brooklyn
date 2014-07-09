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
define([
    "underscore", "model/location"
], function (_, Location) {

    var location = new Location.Model
    location.url = "fixtures/location-summary.json"
    location.fetch({async:false})

    describe('model/location', function () {
        it("loads data from fixture file", function () {
            expect(location.get("spec")).toBe("localhost")
            expect(location.getLinkByName("self")).toBe("/v1/locations/123")
        })
    })

    describe('model/location', function () {
        // keep these in describe so jasmine-maven will load them from the file pointed by URL
        var locationFixtures = new Location.Collection
        locationFixtures.url = 'fixtures/location-list.json'
        locationFixtures.fetch()
        it('loads all model properties defined in fixtures/location-list.json', function () {
            expect(locationFixtures.length).toEqual(1)
            var spec = locationFixtures.at(0)
            expect(spec.get("id")).toEqual('123')
            expect(spec.get("name")).toEqual('localhost')
            expect(spec.get("spec")).toEqual('localhost')
            expect(spec.get("config")).toEqual({})
            expect(spec.hasSelfUrl('/v1/locations/123')).toBeTruthy()
            expect(spec.getLinkByName("self")).toEqual('/v1/locations/123')
        })

        var locationCollection = new Location.Collection()
        it('fetches from /v1/locations', function () {
            expect(locationCollection.url).toEqual('/v1/locations')
        })
        it('has model LocationSpec', function () {
            expect(locationCollection.model).toEqual(Location.Model)
        })
    })
})

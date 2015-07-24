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
    'underscore', 'model/catalog-application'
], function (_, CatalogApplication) {
    var catalogApplication = new CatalogApplication.Model
    catalogApplication.url = 'fixtures/catalog-application.json'
    catalogApplication.fetch({async:false})

    describe('model/catalog-application', function() {
        it('loads data from fixture file', function () {
            expect(catalogApplication.get('id')).toEqual('com.example.app:1.1')
            expect(catalogApplication.get('type')).toEqual('com.example.app')
            expect(catalogApplication.get('name')).toEqual('My example application')
            expect(catalogApplication.get('version')).toEqual('1.1')
            expect(catalogApplication.get('description')).toEqual('My awesome example application, as a catalog item')
            expect(catalogApplication.get('planYaml')).toEqual('services:\n- type: brooklyn.entity.basic.VanillaSoftwareProcess\n  launch.command: echo \"Launch application\"\n  checkRunning.command: echo \"Check running application\"')
            expect(catalogApplication.get('iconUrl')).toEqual('http://my.example.com/icon.png')
        })
    })
    describe("model/catalog-application", function () {
        it('fetches from /v1/locations', function () {
            var catalogApplicationCollection = new CatalogApplication.Collection()
            expect(catalogApplicationCollection.url).toEqual('/v1/catalog/applications')
        })

        // keep these in describe so jasmine-maven will load them from the file pointed by URL
        var catalogApplicationFixture = new CatalogApplication.Collection
        catalogApplicationFixture.url = 'fixtures/catalog-application-list.json'
        catalogApplicationFixture.fetch()

        it('loads all model properties defined in fixtures/catalog-application.json', function () {
            expect(catalogApplicationFixture.length).toEqual(3)

            var catalogApplication1 = catalogApplicationFixture.at(0)
            expect(catalogApplication1.get('id')).toEqual('com.example.app:1.1')
            expect(catalogApplication1.get('type')).toEqual('com.example.app')
            expect(catalogApplication1.get('name')).toEqual('My example application')
            expect(catalogApplication1.get('version')).toEqual('1.1')
            expect(catalogApplication1.get('description')).toEqual('My awesome example application, as a catalog item')
            expect(catalogApplication1.get('planYaml')).toEqual('services:\n- type: brooklyn.entity.basic.VanillaSoftwareProcess\n  launch.command: echo \"Launch application\"\n  checkRunning.command: echo \"Check running application\"')
            expect(catalogApplication1.get('iconUrl')).toEqual('http://my.example.com/icon.png')

            var catalogApplication2 = catalogApplicationFixture.at(1)
            expect(catalogApplication2.get('id')).toEqual('com.example.app:2.0')
            expect(catalogApplication2.get('type')).toEqual('com.example.app')
            expect(catalogApplication2.get('name')).toEqual('My example application')
            expect(catalogApplication2.get('version')).toEqual('2.0')
            expect(catalogApplication2.get('description')).toEqual('My awesome example application, as a catalog item')
            expect(catalogApplication2.get('planYaml')).toEqual('services:\n- type: brooklyn.entity.basic.VanillaSoftwareProcess\n  launch.command: echo \"Launch application\"\n  checkRunning.command: echo \"Check running application\"')
            expect(catalogApplication2.get('iconUrl')).toEqual('http://my.example.com/icon.png')

            var catalogApplication3 = catalogApplicationFixture.at(2)
            expect(catalogApplication3.get('id')).toEqual('com.example.other.app:1.0')
            expect(catalogApplication3.get('type')).toEqual('com.example.other.app')
            expect(catalogApplication3.get('name')).toEqual('Another example application')
            expect(catalogApplication3.get('version')).toEqual('1.0')
            expect(catalogApplication3.get('description')).toEqual('Another awesome example application, as a catalog item')
            expect(catalogApplication3.get('planYaml')).toEqual('services:\n- type: brooklyn.entity.basic.VanillaSoftwareProcess\n  launch.command: echo \"Launch other application\"\n  checkRunning.command: echo \"Check running other application\"')
            expect(catalogApplication3.get('iconUrl')).toEqual('http://my.other.example.com/icon.png')
        })

        it ('Collection#getDistinctApplications returns all available applications, group by type', function() {
            var groupBy = catalogApplicationFixture.getDistinctApplications()

            expect(Object.keys(groupBy).length).toBe(2)
            expect(groupBy.hasOwnProperty('com.example.app')).toBeTruthy()
            expect(groupBy['com.example.app'].length).toBe(2)
            expect(groupBy['com.example.app'][0].get('version')).toEqual('1.1')
            expect(groupBy['com.example.app'][1].get('version')).toEqual('2.0')
            expect(groupBy.hasOwnProperty('com.example.other.app')).toBeTruthy()
            expect(groupBy['com.example.other.app'].length).toBe(1)
            expect(groupBy['com.example.other.app'][0].get('version')).toEqual('1.0')
        })

        it('Collection#getTypes() returns only distinct types', function() {
            var types = catalogApplicationFixture.getTypes()

            expect(types.length).toBe(2)
            expect(types[0]).toEqual('com.example.app')
            expect(types[1]).toEqual('com.example.other.app')
        })

        describe('Collection#hasType()', function() {
            it('Returns true if the given type exists within the applications list', function() {
                var ret = catalogApplicationFixture.hasType('com.example.other.app')

                expect(ret).toBeTruthy()
            })

            it('Returns false if the given type exists within the applications list', function() {
                var ret = catalogApplicationFixture.hasType('com.example.other.app.that.does.not.exist')

                expect(ret).toBeFalsy()
            })
        })

        describe('Collection#getVersions()', function() {
            it('Returns an empty array if no applications exist with the given type', function() {
                var versions = catalogApplicationFixture.getVersions('com.example.other.app.that.does.not.exist')

                expect(versions.length).toBe(0)
            })

            it('Returns the expected array of versions if applications exist with the given type', function() {
                var versions = catalogApplicationFixture.getVersions('com.example.app')

                expect(versions.length).toBe(2)
                expect(versions[0]).toEqual('1.1')
                expect(versions[1]).toEqual('2.0')
            })
        })
    })
})
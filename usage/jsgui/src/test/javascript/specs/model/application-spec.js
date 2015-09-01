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
    "model/application", "model/entity"
], function (Application, Entity) {

    $.ajaxSetup({ async:false });

    describe('model/application Application model', function () {

        var application = new Application.Model()

        application.url = 'fixtures/application.json'
        application.fetch({async:false})

        it('loads all model properties defined in fixtures/application.json', function () {
            expect(application.get("status")).toEqual('STARTING')
            expect(application.getLinkByName('self')).toEqual('/v1/applications/myapp')
            expect(application.getLinkByName('entities')).toEqual('fixtures/entity-summary-list.json')
        })

        it("loads the spec from fixtures/application.json", function () {
            var applicationSpec = application.getSpec(),
                entity = new Entity.Model(applicationSpec.get("entities")[0])

            expect(applicationSpec.get("name")).toEqual('myapp')
            expect(applicationSpec.get("locations")[0]).toEqual('/v1/locations/1')
            expect(entity.get("name")).toEqual('Vanilla Java App')
        })

        it('fetches entities from the spec url: fixtures/entity-summary-list.json', function () {
            expect(application.getLinkByName('entities')).toBe('fixtures/entity-summary-list.json')
        })
    })

    describe('model/application', function () {

        var spec, location, entity

        beforeEach(function () {
            spec = new Application.Spec
            location = "/v1/locations/2"
            entity = new Entity.Model({name:'test'})

            spec.url = 'fixtures/application-spec.json'
            spec.fetch({async:false})
        })

        it('loads the properties from fixtures/application-spec.json', function () {
            expect(spec.get("name")).toEqual('myapp')
            expect(spec.get("locations")[0]).toEqual('/v1/locations/1')
            expect(spec.get("entities").length).toBe(1)
        })

        it("loads the entity from fixtures/application-spec.json", function () {
            var entity = new Entity.Model(spec.get("entities")[0])
            expect(entity.get("name")).toEqual('Vanilla Java App')
            expect(entity.get("type")).toEqual('org.apache.brooklyn.entity.java.VanillaJavaApp')
            expect(entity.getConfigByName('initialSize')).toEqual('1')
            expect(entity.getConfigByName('creationScriptUrl')).toEqual('http://my.brooklyn.io/storage/foo.sql')
        })

        it("triggers 'change' when we add a location", function () {
            spyOn(spec, "trigger").andCallThrough()
            spec.addLocation(location)
            expect(spec.trigger).toHaveBeenCalled()
            expect(spec.get("locations").length).toEqual(2)
        })

        it("triggers 'change' when we remove a location", function () {
            spec.addLocation(location)
            spyOn(spec, "trigger").andCallThrough()

            spec.removeLocation('/v1/invalid/location')
            expect(spec.trigger).not.toHaveBeenCalled()
            spec.removeLocation(location)
            expect(spec.trigger).toHaveBeenCalled()
            expect(spec.get("locations").length).toEqual(1)
        })

        it('allows you to add the same location twice', function () {
            var spec = new Application.Spec,
                location = '/ion/23'
            spec.addLocation(location)
            spec.addLocation(location)
            expect(spec.get("locations").length).toEqual(2)
        })

        it("triggers 'change' when you add an entity", function () {
            spyOn(spec, "trigger").andCallThrough()
            spec.removeEntityByName(entity.get("name"))
            expect(spec.trigger).not.toHaveBeenCalled()
            spec.addEntity(entity)
            expect(spec.trigger).toHaveBeenCalled()
        })

        it("triggers 'change' when you remove an entity", function () {
            spec.addEntity(entity)
            spyOn(spec, "trigger").andCallThrough()
            spec.removeEntityByName(entity.get("name"))
            expect(spec.trigger).toHaveBeenCalled()
        })

        it('allows you to add the same entity twice', function () {
            var spec = new Application.Spec,
                entity = new Entity.Model({ name:'test-entity'})
            spec.addEntity(entity)
            spec.addEntity(entity)
            expect(spec.get("entities").length).toEqual(2)
        })
    })
})

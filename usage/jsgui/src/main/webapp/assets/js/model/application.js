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
/**
 * Models an application.
 */
define([
    "underscore", "backbone"
], function (_, Backbone) {

    var Application = {}

    Application.Spec = Backbone.Model.extend({
        defaults:function () {
            return {
                id:null,
                name:"",
                type:null,
                entities:null,
                locations:[]
            }
        },
        hasLocation:function (location) {
            if (location) return _.include(this.get('locations'), location)
        },
        addLocation:function (location) {
            var locations = this.get('locations')
            locations.push(location)
            this.set('locations', locations)
            this.trigger("change")
            this.trigger("change:locations")
        },
        removeLocation:function (location) {
            var newLocations = [],
                currentLocations = this.get("locations")
            for (var index in currentLocations) {
                if (currentLocations[index] != location && index != null)
                    newLocations.push(currentLocations[index])
            }
            this.set('locations', newLocations)
        },
        removeLocationIndex:function (locationNumber) {
            var newLocations = [],
                currentLocations = this.get("locations")
            for (var index=0; index<currentLocations.length; index++) {
                if (index != locationNumber)
                    newLocations.push(currentLocations[index])
            }
            this.set('locations', newLocations)
        },
        setLocationAtIndex:function (locationNumber, val) {
            var newLocations = [],
                currentLocations = this.get("locations")
            for (var index=0; index<currentLocations.length; index++) {
                if (index != locationNumber)
                    newLocations.push(currentLocations[index])
                else
                    newLocations.push(val)
            }
            this.set('locations', newLocations)
        },
        getEntities: function() {
            var entities = this.get('entities')
            if (entities === undefined) return [];
            return entities;
        },
        addEntity:function (entity) {
            var entities = this.getEntities()
            if (!entities) {
                entities = []
                this.set("entities", entities)
            }
            entities.push(entity.toJSON())
            this.set('entities', entities)
            this.trigger("change")
            this.trigger("change:entities")
        },
        removeEntityIndex:function (indexToRemove) {
            var newEntities = [],
                currentEntities = this.getEntities()
            for (var index=0; index<currentEntities.length; index++) {
                if (index != indexToRemove)
                    newEntities.push(currentEntities[index])
            }
            this.set('entities', newEntities)
        },
        removeEntityByName:function (name) {
            var newEntities = [],
                currentEntities = this.getEntities()
            for (var index in currentEntities) {
                if (currentEntities[index].name != name)
                    newEntities.push(currentEntities[index])
            }
            this.set('entities', newEntities)
        },
        hasEntityWithName:function (name) {
            return _.any(this.getEntities(), function (entity) {
                return entity.name === name
            })
        }
    })

    Application.Model = Backbone.Model.extend({
        defaults:function () {
            return{
                id:null,
                spec:{},
                status:"UNKNOWN",
                links:{}
            }
        },
        initialize:function () {
            this.id = this.get("id")
        },
        getSpec:function () {
            return new Application.Spec(this.get('spec'))
        },
        getLinkByName:function (name) {
            if (name) return this.get("links")[name]
        }
    })

    Application.Collection = Backbone.Collection.extend({
        model:Application.Model,
        url:'/v1/applications'
    })

    return Application
})


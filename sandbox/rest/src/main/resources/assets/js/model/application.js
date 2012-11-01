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
                name:"",
                entities:[],
                locations:[]
            }
        },
        hasLocation:function (location) {
            if (location) return _.include(this.get('locations'), location)
        },
        addLocation:function (location) {
            if (!this.hasLocation(location)) {
                var locations = this.get('locations')
                locations.push(location)
                this.set('locations', locations)
                this.trigger("change")
                this.trigger("change:locations")
            }
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
        addEntity:function (entity) {
            var entities = this.get('entities')
            if (!this.hasEntityWithName(entity.get("name"))) {
                entities.push(entity.toJSON())
                this.set('entities', entities)
                this.trigger("change")
                this.trigger("change:entities")
            }
        },
        removeEntityByName:function (name) {
            var newEntities = [],
                currentEntities = this.get("entities")
            for (var index in currentEntities) {
                if (currentEntities[index].name != name)
                    newEntities.push(currentEntities[index])
            }
            this.set('entities', newEntities)
        },
        hasEntityWithName:function (name) {
            return _.any(this.get('entities'), function (entity) {
                return entity.name === name
            })
        }
    })

    Application.Model = Backbone.Model.extend({
        defaults:function () {
            return{
                spec:{},
                status:"UNKNOWN",
                links:{}
            }
        },
        initialize:function () {
            this.id = this.get("spec")["name"]
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


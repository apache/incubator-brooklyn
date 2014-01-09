define(["underscore", "backbone"], function (_, Backbone) {

    var EntitySummary = {}

    EntitySummary.Model = Backbone.Model.extend({
        defaults:function () {
            return {
                'id':'',
                'name':'',
                'type':'',
                'links':{}
            }
        },
        getLinkByName:function (name) {
            if (name) return this.get("links")[name]
        },
        getDisplayName:function () {
            var name = this.get("name")
            if (name) return name;
            var type = this.get("type")
            var appId = this.getLinkByName('self')
            if (type && appId) {
                return type.slice(type.lastIndexOf('.') + 1) + ':' + appId.slice(appId.lastIndexOf('/') + 1)
            }
        },
        getSensorUpdateUrl:function () {
            return this.getLinkByName("self") + "/sensors/current-state"
        },
        getConfigUpdateUrl:function () {
            return this.getLinkByName("self") + "/config/current-state"
        }
    })

    EntitySummary.Collection = Backbone.Collection.extend({
        model:EntitySummary.Model,
        url:'entity-summary-collection',
        findByDisplayName:function (displayName) {
            if (displayName) return this.filter(function (element) {
                return element.getDisplayName() === displayName
            })
        }
    })

    return EntitySummary
})
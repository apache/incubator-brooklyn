define(["underscore", "backbone"], function (_, Backbone) {

    var Entity = {}

    Entity.Model = Backbone.Model.extend({
        defaults:function () {
            return {
                name:"",
                type:"",
                config:{}
            }
        },
        getConfigByName:function (key) {
            if (key) return this.get("config")[key]
        },
        addConfig:function (key, value) {
            if (key) {
                var configs = this.get("config")
                configs[key] = value
                this.set('config', configs)
                this.trigger("change")
                this.trigger("change:config")
                return true
            }
            return false
        },
        removeConfig:function (key) {
            if (key) {
                var configs = this.get('config')
                delete configs[key]
                this.set('config', configs)
                this.trigger("change")
                this.trigger("change:config")
                return true
            }
            return false
        }
    })

    Entity.Collection = Backbone.Collection.extend({
        model:Entity.Model,
        url:'entity-collection'
    })

    return Entity
})
define([
    "jquery", 'backbone'
], function ($, Backbone) {

    var ConfigSummary = {}

    ConfigSummary.Model = Backbone.Model.extend({
        defaults:function () {
            return {
                name:'',
                type:'',
                description:'',
                links:{}
            }
        },
        getLinkByName:function (name) {
            if (name) return this.get("links")[name]
        }
    })

    ConfigSummary.Collection = Backbone.Collection.extend({
        model:ConfigSummary.Model
    })

    return ConfigSummary
})
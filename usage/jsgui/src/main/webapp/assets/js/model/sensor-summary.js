define([
    "jquery", 'backbone'
], function ($, Backbone) {

    var SensorSummary = {}

    SensorSummary.Model = Backbone.Model.extend({
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

    SensorSummary.Collection = Backbone.Collection.extend({
        model:SensorSummary.Model
    })

    return SensorSummary
})
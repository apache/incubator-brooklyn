define([
    "underscore", "backbone"
], function (_, Backbone) {

    var EffectorSummary = {}

    EffectorSummary.Model = Backbone.Model.extend({
        idAttribute: 'name',
        defaults:function () {
            return {
                name:"",
                description:"",
                returnType:"",
                parameters:[],
                links:{
                    self:"",
                    entity:"",
                    application:""
                }
            }
        },
        getParameterByName:function (name) {
            if (name) {
                return _.find(this.get("parameters"), function (param) {
                    return param.name == name
                })
            }
        },
        getLinkByName:function (name) {
            if (name) return this.get("links")[name]
        }
    })

    EffectorSummary.Collection = Backbone.Collection.extend({
        model:EffectorSummary.Model
    })

    return EffectorSummary
})
define([
    "underscore", "backbone"
], function (_, Backbone) {

    var PolicySummary = {}

    PolicySummary.Model = Backbone.Model.extend({
        defaults:function () {
            return {
                id:"",
                name:"",
                state:"",
                links:{
                    self:"",
                    config:"",
                    start:"",
                    stop:"",
                    destroy:"",
                    entity:"",
                    application:""
                }
            }
        },
        getLinkByName:function (name) {
            if (name) return this.get("links")[name]
        },
        getPolicyConfigUpdateUrl:function () {
            return this.getLinkByName("self") + "/config/current-state"
        }
    })

    PolicySummary.Collection = Backbone.Collection.extend({
        model:PolicySummary.Model
    })

    return PolicySummary
})
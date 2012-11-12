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
        }
    })

    PolicySummary.Collection = Backbone.Collection.extend({
        model:PolicySummary.Model
    })

    return PolicySummary
})
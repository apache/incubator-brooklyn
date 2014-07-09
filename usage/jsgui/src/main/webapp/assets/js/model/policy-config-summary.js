define([
    "underscore", "backbone"
], function (_, Backbone) {

    var PolicyConfigSummary = {}

    PolicyConfigSummary.Model = Backbone.Model.extend({
        defaults:function () {
            return {
                name:"",
                type:"",
                description:"",
                defaultValue:"",
                value:"",
                reconfigurable:"",
                links:{
                    self:"",
                    application:"",
                    entity:"",
                    policy:"",
                    edit:""
                }
            }
        },
        getLinkByName:function (name) {
            if (name) return this.get("links")[name]
        }
    })

    PolicyConfigSummary.Collection = Backbone.Collection.extend({
        model:PolicyConfigSummary.Model
    })

    return PolicyConfigSummary
})
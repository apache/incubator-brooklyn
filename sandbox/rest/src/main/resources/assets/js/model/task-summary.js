define([
    "underscore", "backbone"
], function (_, Backbone) {

    var TaskSummary = {}

    TaskSummary.Model = Backbone.Model.extend({
        defaults:function () {
            return {
                entityId:"",
                entityDisplayName:"",
                displayName:"",
                description:"",
                id:"",
                tags:{},
                rawSubmitTimeUtc:-1,
                submitTimeUtc:"",
                startTimeUtc:"",
                endTimeUtc:"",
                currentStatus:"",
                detailedStatus:""
            }
        },
        getTagByName:function (name) {
            if (name) return this.get("tags")[name]
        }
    })

    TaskSummary.Collection = Backbone.Collection.extend({
        model:TaskSummary.Model
    })

    return TaskSummary
})
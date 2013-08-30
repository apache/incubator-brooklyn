define([
    "underscore", "backbone"
], function (_, Backbone) {

    var TaskSummary = {}

    TaskSummary.Model = Backbone.Model.extend({
        defaults:function () {
            return {
                id:"",
                links:{},
                displayName:"",
                description:"",
                entityId:"",
                entityDisplayName:"",
                tags:{},
                submitTimeUtc:0,
                startTimeUtc:0,
                endTimeUtc:0,
                currentStatus:"",
                children:[],
                // missing a few -- submittedTask, blockingXxx -- but that seems okay
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
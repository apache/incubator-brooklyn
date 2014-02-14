define([
    "underscore", "backbone"
], function (_, Backbone) {

    var TaskSummary = {};

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
                submitTimeUtc:null,
                startTimeUtc:null,
                endTimeUtc:null,
                currentStatus:"",
                result:null,
                isError:null,
                isCancelled:null,
                children:[],
                detailedStatus:"",
                // missing some from TaskSummary (e.g. streams, isError), 
                // but that's fine, worst case they come back null / undefined
            };
        },
        getTagByName:function (name) {
            if (name) return this.get("tags")[name];
        },
        isError: function() { return this.attributes.isError==true; },
        isGlobalTopLevel: function() {
            return this.attributes.submittedByTask == null;
        },
        isLocalTopLevel: function() {
            var submitter = this.attributes.submittedByTask;
            return (submitter==null ||
                    (submitter.metadata && submitter.metadata.id != this.id)); 
        },
    });

    TaskSummary.Collection = Backbone.Collection.extend({
        model:TaskSummary.Model
    });

    return TaskSummary;
});

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
                blockingTask:null,
                blockingDetails:null,
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
        
        // added from https://github.com/jashkenas/backbone/issues/1069#issuecomment-17511573
        // to clear attributes locally if they aren't in the server-side function
        parse: function(resp) {
            _.keys(this.attributes).forEach(function(key) {
              if (resp[key] === undefined) {
                resp[key] = undefined;
              }
            });

            return resp;
        }
    });

    TaskSummary.Collection = Backbone.Collection.extend({
        model:TaskSummary.Model
    });

    return TaskSummary;
});

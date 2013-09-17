define([
    "underscore", "backbone"
], function (_, Backbone) {

    var AppTree = {}

    AppTree.Model = Backbone.Model.extend({
        defaults:function () {
            return {
                id:"",
                name:"",
                type:"",
                iconUrl:"",
                serviceUp:"",
                serviceState:"",
                applicationId:"",
                parentId:"",
                childrenIds:[]
            }
        },
        getDisplayName:function () {
            return this.get("name")
        },
        hasChildren:function () {
            return this.get("childrenIds").length > 0
        }
    })

    AppTree.Collection = Backbone.Collection.extend({
        model:AppTree.Model,
        includedEntities: [],
        getApplications: function () {
            var entities = [];
            _.each(this.models, function(it) { if (it.get('id')==it.get('applicationId')) entities.push(it.get('id')) });
            return entities;
        },
        getNonApplications: function () {
            var entities = [];
            _.each(this.models, function(it) { if (it.get('id')!=it.get('applicationId')) entities.push(it.get('id')) });
            return entities;
        },
        includeEntities: function (entities) {
            var oldLength = this.includedEntities.length;
            this.includedEntities = _.uniq(this.includedEntities.concat(entities))
            return (this.includedEntities.length > oldLength);
        },
        url: function() {
            if (this.includedEntities.length)
                return "/v1/applications/fetch?items="+this.includedEntities.join(",");
            else
                return "/v1/applications/fetch";
        }
    })

    return AppTree
})
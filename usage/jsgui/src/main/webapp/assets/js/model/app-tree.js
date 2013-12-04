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
                children:[]
            }
        },
        getDisplayName:function () {
            return this.get("name")
        },
        hasChildren:function () {
            return this.get("children").length > 0
        }
    })

    AppTree.Collection = Backbone.Collection.extend({
        model: AppTree.Model,
        includedEntities: [],

        getApplications: function () {
            var entities = [];
            _.each(this.models, function(it) {
                if (it.get('id') == it.get('applicationId'))
                    entities.push(it.get('id'));
            });
            return entities;
        },
        getNonApplications: function () {
            var entities = [];
            _.each(this.models, function(it) {
                if (it.get('id') != it.get('applicationId'))
                    entities.push(it.get('id'));
            });
            return entities;
        },
        includeEntities: function (entities) {
            var oldLength = this.includedEntities.length;
            this.includedEntities = _.uniq(this.includedEntities.concat(entities))
            return (this.includedEntities.length > oldLength);
        },
        /**
         * Depth-first search of entries in this.models for the first entity whose ID matches the
         * function's argument. Includes each entity's children.
         */
        getEntityNameFromId: function (id) {
            if (!this.models.length) return undefined;

            for (var i = 0, l = this.models.length; i < l; i++) {
                var model = this.models[i];
                if (model.get("id") === id) {
                    return model.getDisplayName()
                } else {
                    // slice(0) makes a shallow clone of the array
                    var queue = model.get("children").slice(0);
                    while (queue.length) {
                        var child = queue.pop();
                        if (child.id === id) {
                            return child.name;
                        } else {
                            if (_.has(child, 'children')) {
                                queue = queue.concat(child.children);
                            }
                        }
                    }
                }
            }

            // Is this surprising? If we returned undefined and the caller concatenates it with
            // a string they'll get "stringundefined", whereas this way they'll just get "string".
            return "";
        },
        url: function() {
            if (this.includedEntities.length) {
                var ids = _.pluck(this.includedEntities, "id").join(",");
                return "/v1/applications/fetch?items="+ids;
            } else
                return "/v1/applications/fetch";
        }
    })

    return AppTree
})
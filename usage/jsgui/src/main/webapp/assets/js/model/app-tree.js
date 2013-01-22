define([
    "backbone"
], function (Backbone) {

    var AppTree = {}

    AppTree.Model = Backbone.Model.extend({
        defaults:function () {
            return {
                id:"",
                name:"",
                children:[]
            }
        },
        getDisplayName:function () {
            return this.get("name") //+ ":" + this.get("id")
        },
        hasChildren:function () {
            return this.get("children").length > 0
        }
    })

    AppTree.Collection = Backbone.Collection.extend({
        model:AppTree.Model,
        url:"/v1/applications/tree"
    })

    return AppTree
})
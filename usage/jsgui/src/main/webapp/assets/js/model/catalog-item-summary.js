define(["underscore", "backbone"], function (_, Backbone) {

    // not used currently
    
    var CatalogItem = {}

    CatalogItem.Model = Backbone.Model.extend({
        defaults:function () {
            return {
                id:"",
                name:"",
                type:"",
                description:"",
                iconUrl:""
            }
        }
    })

    CatalogItem.Collection = Backbone.Collection.extend({
        model:CatalogItem.Model,
        url:'/v1/catalog'  // not used?
    })

    return CatalogItem
})
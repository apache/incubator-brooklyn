define(["underscore", "backbone"], function (_, Backbone) {

    var Location = {}

    Location.Model = Backbone.Model.extend({
        urlRoot:'/v1/locations',
        defaults:function () {
            return {
                id:'',
                name:'',
                spec:'',
                config:{},
                links:{
                    self:''
                }
            }
        },
        idFromSelfLink:function () {
            // TODO replace with get('id')
            return this.get('id');
//            if (this.has('links')) {
//                var links = this.get('links')
//                if (links['self'] && links['self'] != '') {
//                    var s = links['self']
//                    var id = s.substring(s.lastIndexOf('/') + 1)
//                    if (_.isEqual(s, this.urlRoot + '/' + id)) return id
//                }
//            }
//            return void 0
        },
        initialize:function () {
//            this.set({'id':this.idFromSelfLink()})
        },
        addConfig:function (key, value) {
            if (key) {
                var configs = this.get("config")
                configs[key] = value
                this.set('config', configs)
                return true
            }
            return false
        },
        removeConfig:function (key) {
            if (key) {
                var configs = this.get('config')
                delete configs[key]
                this.set('config', configs)
                return true
            }
            return false
        },
        getConfigByName:function (name) {
            if (name) return this.get("config")[name]
        },
        getLinkByName:function (name) {
            if (name) return this.get("links")[name]
        },
        hasSelfUrl:function (url) {
            return (this.getLinkByName("self") === url)
        },
        getPrettyName: function() {
            var name = this.get('name')
            if (name!=null && name.length>0) return name
            return this.get('spec')
//            var suffix=this.getConfigByName("location");
//            if (suffix==null) suffix=this.getConfigByName("endpoint")
//            if (suffix!=null) suffix=":"+suffix; else suffix="";
//            return this.get("provider")+suffix
        }

    })

    Location.Collection = Backbone.Collection.extend({
        model:Location.Model,
        url:'/v1/locations'
    })

    Location.UsageLocated = Backbone.Model.extend({
        url:'/v1/locations/usage/LocatedLocations'
    })

    return Location
})
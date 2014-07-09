define([
    "underscore", "jquery", "backbone", "text!tpl/catalog/details-location.html"
], function (_, $, Backbone, LocationDetailsHtml) {
    /**
     * Renders one location element.
     */
    var LocationDetailsView = Backbone.View.extend({
        template:_.template(LocationDetailsHtml),

        initialize:function () {
//            this.model.bind('change', this.render, this)
            this.model.bind('destroy', this.close, this)
        },

        render:function (eventName) {
            this.$el.html(this.template({
                title: this.model.getPrettyName(),
                id: this.model.id,
                name: this.model.get('name'),
                spec: this.model.get('spec'),
                config: this.model.get("config")
            }))
            if (_.size(this.model.get("config"))==0) {
                $(".has-no-config", this.$el).show()
            }
            
            return this
        },

        close:function (eventName) {
//            this.$el.unbind()
//            this.$el.remove()
        }
    })

    return LocationDetailsView
})
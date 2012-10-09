define([
    "underscore", "jquery", "backbone", "text!tpl/catalog/location-row.html"
], function (_, $, Backbone, LocationRowHtml) {
    /**
     * Renders one location element.
     */
    var LocationRowView = Backbone.View.extend({
        tagName:'tr',

        template:_.template(LocationRowHtml),

        initialize:function () {
            this.model.bind('change', this.render, this)
            this.model.bind('destroy', this.close, this)
        },

        render:function (eventName) {
            this.$el.html(this.template(this.model.toJSON()))
            return this
        },

        close:function (eventName) {
            this.$el.unbind()
            this.$el.remove()
        }
    })

    return LocationRowView
})
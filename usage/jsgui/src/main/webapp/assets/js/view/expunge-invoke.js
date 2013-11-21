/**
 * Render entity expungement as a modal
 */
define([
    "underscore", "jquery", "backbone",
    "text!tpl/apps/expunge-modal.html"
], function(_, $, Backbone, ExpungeModalHtml) {
    return Backbone.View.extend({
        template: _.template(ExpungeModalHtml),
        events: {
            "click .invoke-expunge": "invokeExpunge",
            "hidden": "hide"
        },
        render: function() {
            this.$el.html(this.template(this.model));
            return this;
        },
        invokeExpunge: function() {
            console.log("Expunge invoked on " + this.model.name);
            this.$el.fadeTo(500, 0.5);
            this.$el.modal("hide");
        },
        hide: function() {
            this.undelegateEvents();
        }
    });
});
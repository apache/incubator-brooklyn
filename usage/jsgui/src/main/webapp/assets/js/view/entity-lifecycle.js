/**
 * Render entity lifecycle tab.
 *
 * @type {*}
 */
define(["underscore", "jquery", "backbone",
    "text!tpl/apps/lifecycle.html", "view/expunge-invoke"
], function(_, $, Backbone, LifecycleHtml, ExpungeInvokeView) {
    var EntityLifecycleView = Backbone.View.extend({
        template: _.template(LifecycleHtml),
        events: {
            "click #expunge": "showExpungeModal"
        },
        initialize:function() {
            _.bindAll(this);
            this.$el.html(this.template());
        },
        showExpungeModal: function() {
            var self = this;
            var modal = new ExpungeInvokeView({
                el:"#expunge-modal",
                model:this.model.attributes
            });
            modal.on("entity.expunged", function() {
                self.trigger("entity.expunged");
            });
            modal.render().$el.modal("show");
        }
    });
    return EntityLifecycleView;
});
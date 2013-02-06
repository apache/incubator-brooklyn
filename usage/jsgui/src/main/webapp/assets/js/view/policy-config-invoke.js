/**
 * Render a policy configuration key as a modal.
 */
define([
    "underscore", "jquery", "backbone", "text!tpl/apps/policy-config-modal.html", "bootstrap"
], function (_, $, Backbone, PolicyConfigModalHtml) {

    var PolicyConfigInvokeView = Backbone.View.extend({
        template:_.template(PolicyConfigModalHtml),
        events:{
            "click .save-policy-config":"savePolicyConfig",
            "shown":"unfade"
        },
        render:function () {
            var that = this,
            	configUrl = this.model.getLinkByName("self")
            $.get(configUrl, function (data) { 
            	that.$el.html(that.template({
                    name:that.model.get("name"),
                    description:that.model.get("description"),
                    type:that.model.get("type"),
                    value:data,
                    policyName:that.options.policy.get("name"),
                }))
            })
            return this
        },
        unfade: function() {
            this.$el.fadeTo(500,1);
        },
        savePolicyConfig:function () {
            var that = this,
                url = this.model.getLinkByName("self") + "/set",
                val = this.$("#policy-config-value").val()
            this.$el.fadeTo(500,0.5);
            $.ajax({
                type:"POST",
                url:url+"?value="+val,
                success:function (data) {
                    that.$el.modal("hide")
                    that.$el.fadeTo(500,1);
                },
                error:function(data) {
                    that.$el.fadeTo(100,1).delay(200).fadeTo(200,0.2).delay(200).fadeTo(200,1);
                    // TODO render the error better than poor-man's flashing
                    // (would just be connection error -- with timeout=0 we get a task even for invalid input)
                    log("ERROR setting config")
                    log(data)
                }})
            // un-delegate events
            this.undelegateEvents()
        }
    })
    return PolicyConfigInvokeView
})

/**
 * Render the policies tab.You must supply the model and optionally the element
 * on which the view binds itself.
 *
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "model/policy-summary",
    "text!tpl/apps/policy.html", "text!tpl/apps/policy-row.html", "bootstrap"
], function (_, $, Backbone, PolicySummary, PolicyHtml, PolicyRowHtml) {

    var EntityPoliciesView = Backbone.View.extend({
        template:_.template(PolicyHtml),
        policyRow:_.template(PolicyRowHtml),
        events:{
            "click .policy-start":"callStart",
            "click .policy-stop":"callStop",
            "click .policy-destroy":"callDestroy",
        },
        initialize:function () {
            this.$el.html(this.template({}))
            var that = this
            this._policies = new PolicySummary.Collection()
            // fetch the list of policies and create a view for each one
            this._policies.url = this.model.getLinkByName("policies")
            this.callPeriodically("entity-policies", function() { that.refresh() }, 3000)
            this.refresh()
        },
        refresh: function() {
            var that = this
            this._policies.fetch({success:function () {
                that.render()
            }})
        },
        render:function () {
            var that = this
            var $tableBody = this.$('#policies-table tbody').empty()
            if (this._policies.length==0) {
                this.$(".has-no-policies").show();
            } else {                
                this.$(".has-no-policies").hide();
                this._policies.each(function (policy) {
                    $tableBody.append(that.policyRow({ 
                        name: policy.get("name"),
                        state: policy.get("state"),
                        summary: policy
                    }))
                })
            }
            return this
        },
        callStart: function(event) { this.doPost(event, "start") },
        callStop: function(event) { this.doPost(event, "stop") },
        callDestroy: function(event) { this.doPost(event, "destroy") },
        doPost: function(event, linkname) {
            var that = this
            var url = $(event.currentTarget).attr("link");
            // trigger the event by ajax with attached parameters
            $.ajax({
                type:"POST",
                url:url,
                success: function() {
                    that.refresh()
                }})
        }
    })
    return EntityPoliciesView
})
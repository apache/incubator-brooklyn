/**
 * Render the effectors tab.You must supply the model and optionally the element
 * on which the view binds itself.
 *
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "model/effector-summary",
    "view/effector-invoke", "text!tpl/apps/effector.html", "text!tpl/apps/effector-row.html", "bootstrap"
], function (_, $, Backbone, EffectorSummary, EffectorInvokeView, EffectorHtml, EffectorRowHtml) {

    var EntityEffectorsView = Backbone.View.extend({
        template:_.template(EffectorHtml),
        effectorRow:_.template(EffectorRowHtml),
        events:{
            "click .show-effector-modal":"showEffectorModal"
        },
        initialize:function () {
            this.$el.html(this.template({}))
            var that = this
            this._effectors = new EffectorSummary.Collection()
            // fetch the list of effectors and create a view for each one
            this._effectors.url = this.model.getLinkByName("effectors")
            this._effectors.fetch({success:function () {
                that.render()
            }})
        },
        render:function () {
            var that = this
            var $tableBody = this.$('#effectors-table tbody').empty()
            if (this._effectors.length==0) {
                this.$(".has-no-effectors").show();
            } else {                
                this.$(".has-no-effectors").hide();
                this._effectors.each(function (effector) {
                    $tableBody.append(that.effectorRow({
                        name:effector.get("name"),
                        description:effector.get("description"),
                        cid:effector.cid
                    }))
                })
            }
            return this
        },
        showEffectorModal:function (eventName) {
            // get the model that we need to show, create its view and show it
            var cid = $(eventName.currentTarget).attr("id")
            this._modal = new EffectorInvokeView({
                el:"#effector-modal",
                model:this._effectors.get(cid),
                entity:this.model
            })
            this._modal.render().$el.modal('show')
        }
    })
    return EntityEffectorsView
})
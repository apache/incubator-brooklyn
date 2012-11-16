/**
 * Render an entity effector as a modal.
 */
define([
    "underscore", "jquery", "backbone", "text!tpl/apps/effector-modal.html", "text!tpl/apps/param.html",
    "text!tpl/apps/param-list.html"
], function (_, $, Backbone, EffectorModalHtml, ParamHtml, ParamListHtml) {

    var EffectorView = Backbone.View.extend({
        template:_.template(EffectorModalHtml),
        effectorParam:_.template(ParamHtml),
        effectorParamList:_.template(ParamListHtml),
        events:{
            "click .trigger-effector":"triggerEffector"
        },
        render:function () {
            var that = this, params = this.model.get("parameters")
            this.$el.html(this.template({
                name:this.model.get("name"),
                entityName:this.options.entity.get("name"),
                description:this.model.get("description")?this.model.get("description"):""
            }))
            // do we have parameters to render?
            if (params.length !== 0) {
                this.$(".modal-body").html(this.effectorParamList({}))
                // select the body of the table we just rendered and append params
                var $tbody = this.$("tbody")
                _(params).each(function (param) {
                    $tbody.append(that.effectorParam({
                        name:param.name,
                        type:param.type,
                        description:param.description?param.description:""
                    }))
                })
            }
            this.$(".modal-body").find('*[rel="tooltip"]').tooltip()
            return this
        },
        extractParamsFromTable:function () {
            var parameters = {}
            // iterate over the rows
            this.$(".effector-param").each(function (index) {
                var key = $(this).find(".param-name").text(),
                    value = $(this).find(".param-value").text()
                // we need to create an object out of the input so it will send as the server expects: java Map
                parameters[key] = $.parseJSON(value)
            })
            return parameters
        },
        triggerEffector:function () {
            var that = this
            var url = this.model.getLinkByName("self")
            var parameters = this.extractParamsFromTable()
            // trigger the event by ajax with attached parameters
            $.ajax({
                type:"POST",
                url:url,
                data:JSON.stringify(parameters),
                contentType:"application/json",
                success:function (data) {
                    // hide the modal
                    that.$el.modal("hide")
                }})
            // un-delegate trigger events
            this.undelegateEvents()
        }
    })
    return EffectorView
})
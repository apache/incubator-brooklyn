/**
 * Render an entity effector as a modal.
 */
define([
    "underscore", "jquery", "backbone", "brooklyn-utils",

    "text!tpl/apps/effector-modal.html",
    "text!tpl/apps/param.html",
    "text!tpl/apps/param-list.html"
], function (_, $, Backbone, Util, EffectorModalHtml, ParamHtml, ParamListHtml) {

    var EffectorInvokeView = Backbone.View.extend({
        template:_.template(EffectorModalHtml),
        effectorParam:_.template(ParamHtml),
        effectorParamList:_.template(ParamListHtml),
        events:{
            "click .invoke-effector":"invokeEffector",
            "shown":"unfade"
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
        unfade: function() {
            this.$el.fadeTo(500,1);
        },
        extractParamsFromTable:function () {
            var parameters = {}
            // iterate over the rows
            this.$(".effector-param").each(function (index) {
                var key = $(this).find(".param-name").text(),
                    value = $(this).find(".param-value").val()
                // we need to create an object out of the input so it will send as the server expects: java Map
                parameters[key] = $.parseJSON(value)
            })
            return parameters
        },
        invokeEffector:function () {
            var that = this
            var url = this.model.getLinkByName("self")
            var parameters = this.extractParamsFromTable()
            this.$el.fadeTo(500,0.5);
            $.ajax({
                type:"POST",
                url:url+"?timeout=0",
                data:JSON.stringify(parameters),
                contentType:"application/json",
                success:function (data) {
                    that.$el.modal("hide")
                    that.$el.fadeTo(500,1);
                    // data.id contains the task, if we wanted to switch to showing it
                    // NB we now timeout immediately, so always run in background
                    // ideally we might have a timeout of 300ms
                    // switch to task if it is still running
                    // otherwise show the answer
                    // ... or simpler, just switch to task, so response can be shown
                },
                error: function(data) {
                    that.$el.fadeTo(100,1).delay(200).fadeTo(200,0.2).delay(200).fadeTo(200,1);
                    // TODO render the error better than poor-man's flashing
                    // (would just be connection error -- with timeout=0 we get a task even for invalid input)
                    
                    Util.log("ERROR invoking effector")
                    Util.log(data)
                }})
            // un-delegate events
            this.undelegateEvents()
        }
    })
    return EffectorInvokeView
})

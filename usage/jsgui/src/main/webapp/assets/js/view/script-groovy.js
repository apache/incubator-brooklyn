define([
    "underscore", "jquery", "backbone", "brooklyn-utils",
    "view/viewutils",
    "text!tpl/script/groovy.html", 
    
    "jquery-slideto",
    "jquery-wiggle",
    "jquery-ba-bbq",
    "handlebars",
    "bootstrap"
], function (_, $, Backbone, Util, ViewUtils, GroovyHtml) {

    var ScriptGroovyView = Backbone.View.extend({
        tagName:"div",
        events:{
            "click #groovy-ui-container #submit":"submitScript",
            "click #load-example":"loadExample"
        },
        className:"container container-fluid",
        groovyTemplate:_.template(GroovyHtml),

        initialize:function () {
            this.reset();
        },
        reset: function() {
            this.$el.html(_.template(GroovyHtml, {}))
            $(".output", this.$el).hide()
            $(".output .toggler-region", this.$el).hide()
            ViewUtils.attachToggler(this.$el)
        },
        render:function (eventName) {
            return this
        },
        loadExample: function() {
            $(".input textarea").val(
                    'import static brooklyn.entity.basic.Entities.*\n'+
                    '\n'+
                    'println "Last result: "+last\n'+
                    'data.exampleRunCount = (data.exampleRunCount ?: 0) + 1\n'+
                    'println "Example run count: ${data.exampleRunCount}"\n'+
                    '\n'+
                    'println "Application count: ${mgmt.applications.size()}\\n"\n'+
                    '\n'+
                    'mgmt.applications.each { dumpInfo(it) }\n'+
                    '\n'+
                    'return mgmt.applications\n')
        },
        updateTextareaWithData: function($div, data, alwaysShow) {
            ViewUtils.updateTextareaWithData($div, data, alwaysShow, 50, 350) 
        },
        submitScript: function() {
            var that = this;
            var script = $("#groovy-ui-container #script").val()
            $(".output .toggler-region", this.$el).hide()
            $(".output .throbber", this.$el).show()
            $(".output", this.$el).show()
            $.ajax({
                type:"POST",
                url:"/v1/script/groovy",
                data:script,
                contentType:"application/text",
                success:function (data) {
                    $(".output .throbber", that.$el).hide()
                    that.updateTextareaWithData($(".output .result"), data.result, true);
                    that.updateTextareaWithData($(".output .error"), data.problem, false);
                    that.updateTextareaWithData($(".output .stdout"), data.stdout, false);
                    that.updateTextareaWithData($(".output .stderr"), data.stderr, false);
                },
                error: function(data) {
                    $(".output .throbber", that.$el).hide()
                    $("#groovy-ui-container div.error").val("ERROR: "+data)
                    $(".output .error").show()
                    
                    Util.log("ERROR submitting groovy script")
                    Util.log(data)
                }})
        }
        
    })
    
    return ScriptGroovyView
})
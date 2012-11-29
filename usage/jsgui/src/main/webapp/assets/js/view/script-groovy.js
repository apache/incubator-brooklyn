define([
    "underscore", "jquery", "backbone",
    "text!tpl/script/groovy.html", 
    
    "jquery-slideto",
    "jquery-wiggle",
    "jquery-ba-bbq",
    "handlebars",
    "bootstrap",
    "brooklyn-utils"
], function (_, $, Backbone, GroovyHtml) {

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
            $(".toggler-header", this.$el).click(this.toggleNext)
        },
        render:function (eventName) {
            return this
        },
        toggleNext: function(event) {
            var root = $(event.currentTarget).closest(".toggler-header");
            root.toggleClass("user-hidden");
            $(".toggler-icon", root).
                toggleClass("icon-chevron-left").
                toggleClass("icon-chevron-down");
                
            var next = root.next();
            if (root.hasClass("user-hidden")) 
                next.hide('fast');
            else 
                next.show('fast')
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
            var $ta = $("textarea", $div)
            var show = alwaysShow
            if (data !== undefined) {
                $ta.val(data)
                show = true
            } else {
                $ta.val("")
            }
            if (show) {
                log("foo")
                $div.show(100)
                $ta.css("height", 50);
                var height = Math.min($ta.prop("scrollHeight"), 350)
                $ta.css("height", height);
                log(height)
            } else {
                $div.hide()
            }
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
                    $(".output .throbber", this.$el).hide()
                    that.updateTextareaWithData($(".output .result"), data.result, true);
                    that.updateTextareaWithData($(".output .error"), data.problem, false);
                    that.updateTextareaWithData($(".output .stdout"), data.stdout, false);
                    that.updateTextareaWithData($(".output .stderr"), data.stderr, false);
                },
                error: function(data) {
                    $(".output .throbber", this.$el).hide()
                    $("#groovy-ui-container div.error").val("ERROR: "+data)
                    $(".output .error").show()
                    
                    log("ERROR submitting groovy script")
                    log(data)
                }})
        }
        
    })
    
    return ScriptGroovyView
})
define([
    "underscore", "jquery", "backbone",
    "text!tpl/script/groovy.html", 
    
    "jquery-slideto",
    "jquery-wiggle",
    "jquery-ba-bbq",
    "handlebars",
    "bootstrap"
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
        submitScript: function() {
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
                    
                    if (data.result !== undefined)
                        $(".output .result textarea").val(data.result)
                    else
                        $(".output .result textarea").val("")
                    $(".output .result").show()
                    
                    if (data.problem !== undefined) {
                        $(".output .error textarea").val(data.problem)
                        $(".output .error").show()
                    } else {
                        $(".output .error").hide()
                    }

                    if (data.stdout !== undefined) {
                        $(".output .stdout textarea").val(data.stdout)
                        $(".output .stdout").show()
                    } else {
                        $(".output .stdout").hide()
                    }

                    if (data.stderr !== undefined) {
                        $(".output .stderr textarea").val(data.stderr)
                        $(".output .stderr").show()
                    } else {
                        $(".output .stderr").hide()
                    }
                },
                error: function(data) {
                    $(".output .throbber", this.$el).hide()
                    $("#groovy-ui-container div.error").val("ERROR: "+data)
                    $(".output .error").show()
                    
                    // console.log might cause errors in some browsers but that's fine here (already an error)
                    console.log("ERROR submitting script")
                    console.log(data)
                }})
        }
        
    })
    
    return ScriptGroovyView
})
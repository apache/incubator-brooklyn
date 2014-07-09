/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
define([
    "underscore", "jquery", "backbone",
    "view/viewutils",
    "text!tpl/script/groovy.html", 
    
    "jquery-slideto",
    "jquery-wiggle",
    "jquery-ba-bbq",
    "handlebars",
    "bootstrap"
], function (_, $, Backbone, ViewUtils, GroovyHtml) {

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
            that.updateTextareaWithData($(".output .result"), undefined, false, false);
            that.updateTextareaWithData($(".output .error"), undefined, false, false);
            that.updateTextareaWithData($(".output .stdout"), undefined, false, false);
            that.updateTextareaWithData($(".output .stderr"), undefined, false, false);
            $.ajax({
                type:"POST",
                url:"/v1/script/groovy",
                data:script,
                contentType:"application/text",
                success:function (data) {
                    $(".output .throbber", that.$el).hide()
                    that.updateTextareaWithData($(".output .result"), data.result, true, true);
                    that.updateTextareaWithData($(".output .error"), data.problem, false, true);
                    that.updateTextareaWithData($(".output .stdout"), data.stdout, false, true);
                    that.updateTextareaWithData($(".output .stderr"), data.stderr, false, true);
                },
                error: function(data) {
                    $(".output .throbber", that.$el).hide()
                    $("#groovy-ui-container div.error").val("ERROR: "+data)
                    $(".output .error").show()
                    
                    console.error("ERROR submitting groovy script")
                    console.debug(data)
                }})
        }
        
    })
    
    return ScriptGroovyView
})

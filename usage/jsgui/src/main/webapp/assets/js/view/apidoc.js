define([
    "underscore", "jquery", "backbone",
    "text!tpl/script/swagger.html", 
    
    "jquery-slideto",
    "jquery-wiggle",
    "jquery-ba-bbq",
    "handlebars",
    "bootstrap",
    "brooklyn-utils"
], function (_, $, Backbone, SwaggerHtml) {

    var ApidocView = Backbone.View.extend({
        tagName:"div",
        className:"container container-fluid",
        swaggerTemplate:_.template(SwaggerHtml),

        initialize:function () {
        },
        render:function (eventName) {
            this.$el.html(_.template(SwaggerHtml, {}))
            this.loadSwagger()
            return this
        },
        
        loadSwagger: function() {
            require( [
                    "/assets/js/libs/swagger.js",
                    "/assets/js/libs/swagger-ui.js" ],
                    function() {
                        
            var swaggerUi = new SwaggerUi({
                basePath:"",
                discoveryUrl:"/v1/apidoc",
                dom_id:"swagger-ui-container",
                supportHeaderParams: false,
                supportedSubmitMethods: ['get', 'post', 'put'],
                onComplete: function(swaggerApi, swaggerUi){
                        log("Loaded SwaggerUI")
                        log(swaggerApi);
                        log(swaggerUi);
                },
                onFailure: function(data) {
                        log("Unable to Load SwaggerUI");
                        log(data);
                },
                docExpansion: "none"
            });

            swaggerUi.load();
            })
            
        }

    })
    
    return ApidocView
})

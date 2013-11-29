define([
    "underscore", "jquery", "backbone",
    "text!tpl/script/swagger.html",
    
    "jquery-slideto",
    "jquery-wiggle",
    "jquery-ba-bbq",
    "handlebars",
    "bootstrap"
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
        	var that = this;
        	
            require([
               "/assets/js/libs/swagger.js",
                "/assets/js/libs/swagger-ui.js"
            ], function() {
            	$('.throbber', that.$el).hide().delay(100).fadeTo(250, 0.9);
            	$('div#message-bar.swagger-ui-wrap', that.$el).hide().delay(100).fadeTo(250, 1);
                var swaggerUi = new SwaggerUi({
                    basePath:"",
                    discoveryUrl:"/v1/apidoc",
                    dom_id:"swagger-ui-container",
                    supportHeaderParams: false,
                    supportedSubmitMethods: ['get', 'post', 'put'],
                    onComplete: function(swaggerApi, swaggerUi){
                    	that.$el.fadeTo(200, 1);
                    	$('.throbber', that.$el).hide();
                    	$('div#message-bar.swagger-ui-wrap', that.$el).hide();
                        console.info("Loaded SwaggerUI");
                        console.debug(swaggerApi);
                        console.debug(swaggerUi);
                    },
                    onFailure: function(data) {
                    	that.$el.fadeTo(200, 0.2);
                    	$('.throbber', that.$el).hide();
                    	$('div#message-bar.swagger-ui-wrap', that.$el).hide();
                        console.error("Unable to Load SwaggerUI");
                        console.debug(data);
                    },
                    docExpansion: "none"
                });

                swaggerUi.load();
            })
            
        }

    })
    
    return ApidocView
})

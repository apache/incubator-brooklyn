define([
    "underscore", "jquery", "backbone",
    "text!tpl/hack/swagger.html", 
    
    "jquery-slideto",
    "jquery-wiggle",
    "jquery-ba-bbq",
    "handlebars",
    "bootstrap"
], function (_, $, Backbone, SwaggerHtml) {

//    <link href='http://fonts.googleapis.com/css?family=Droid+Sans:400,700' rel='stylesheet' type='text/css'/>
//    <link href='css/screen.css' media='screen' rel='stylesheet' type='text/css'/>
    
//    <script src='/assets/js/libs/jquery.js' type='text/javascript'></script>
    
//    <script src='lib/jquery.slideto.min.js' type='text/javascript'></script>
//    <script src='lib/jquery.wiggle.min.js' type='text/javascript'></script>
//    <script src='lib/jquery.ba-bbq.min.js' type='text/javascript'></script>
//    <script src='lib/handlebars-1.0.rc.1.js' type='text/javascript'></script>
//    <script src='lib/swagger.js' type='text/javascript'></script>
//    <script src='swagger-ui.js' type='text/javascript'></script>
    
    
//    <script src='/assets/js/libs/underscore.js' type='text/javascript'></script>
//    <script src='/assets/js/libs/backbone.js' type='text/javascript'></script>

    var HackView = Backbone.View.extend({
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
                    if(console) {
                        console.log("Loaded SwaggerUI")
                        console.log(swaggerApi);
                        console.log(swaggerUi);
                    }
                },
                onFailure: function(data) {
                    if(console) {
                        console.log("Unable to Load SwaggerUI");
                        console.log(data);
                    }
                },
                docExpansion: "none"
            });

            swaggerUi.load();
            })
            
        }

    })
    
    return HackView
})
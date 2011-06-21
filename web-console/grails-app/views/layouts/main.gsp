<html>
      <head>
          <title><g:layoutTitle default="Brooklyn Web Management Console" /></title>

          <link rel="stylesheet" href="${resource(dir:'css',file:'main.css')}" />
        <link rel="shortcut icon" href="${resource(dir:'images',file:'favicon.ico')}" type="image/x-icon" />
        <g:javascript library="application" />
        <g:javascript library="jquery/jquery" />
        <g:javascript library="jquery/jquery.jstree" />
        <g:javascript library="jquery/jquery.tools.min" />
       	<link rel="stylesheet" type="text/css" href="http://static.flowplayer.org/tools/css/tabs.css" />
        <g:layoutHead />
      </head>
      <body onload="${pageProperty(name:'body.onload')}">
      <g:render template="/shared/banner"/>

                 <div class="body">
                      <g:layoutBody />
                 </div>

      <g:render template="/shared/footer"/>
      </body>
</html>
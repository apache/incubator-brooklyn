<#-- @ftlvariable name="" type="brooklyn.rest.views.SwaggerUiView" -->
<!DOCTYPE html>
<html>
<head>
    <meta charset='utf-8'/>
    <!-- Always force latest IE rendering engine (even in intranet) and Chrome Frame -->
    <meta content='IE=edge,chrome=1' http-equiv='X-UA-Compatible'/>
    <title>Swagger API Explorer</title>
    <link href='http://fonts.googleapis.com/css?family=Droid+Sans:400,700' rel='stylesheet' type='text/css'/>
    <link href='http://ajax.googleapis.com/ajax/libs/jqueryui/1.8.14/themes/smoothness/jquery-ui.css' media='screen'
          rel='stylesheet' type='text/css'/>
    <link href='/swagger-ui/stylesheets/screen.css' media='screen' rel='stylesheet' type='text/css'/>
    <script src='/swagger-ui/javascripts/jquery-1.6.2.min.js' type='text/javascript'></script>
    <script type='text/javascript'>
        function toggle(selector) {
            jQuery(selector).toggle();
            return false;
        }
    </script>
</head>
<body>
<div id="header" style="background-color:#89BF04;">
    <p>Brooklyn REST Api</p>
</div>
<div class='container' id='resources_container'>
    <ul id='resources'>
    <@resourceTemplate resourceList = apiList/>
    </ul>
</div>
<div id='content_message'></div>

<#macro resourceTemplate resourceList>
    <#list resourceList as resource>
    <li class='resource' id='resource_${resource_index}'>
        <div class='heading'>
            <h2>
                <a href='#!/${resource_index}'
                   onclick="toggle('ul#endpoint_list_${resource_index}');">${resource.getResourcePath()}</a>
            </h2>
            <ul class='options'>
                <li>
                    <a href='#!/${resource_index}'
                       id='endpointListTogger_${resource_index}'>Show/Hide</a>
                </li>
                <li>
                    <a href='#operations_list' onclick="toggle('ul#endpoint_list_${resource_index}');">Toggle Operations
                        List</a>
                </li>
                <li>
                    <a href='#parameters_list' onclick="toggle('.resource${resource_index}');">Toggle parameters</a>
                </li>
            </ul>
        </div>
        <ul class='endpoints' id='endpoint_list_${resource_index}' style='display:none'>
            <#list resource.getApis() as documentationEndoint>
                <@apiTemplate api= documentationEndoint apiIndex = documentationEndoint_index resourceIndex = resource_index/>
            </#list>
        </ul>
    </li>
    </#list>
</#macro>

<#macro apiTemplate api apiIndex resourceIndex>
<li class='endpoint'>
    <ul class='operations' id='endpoint_operations_${apiIndex}'>
        <#list api.getOperations() as operation>
        <@operationTemplate operation = operation endpoint= api apiIndex= apiIndex operationIndex = operation_index resourceIndex = resourceIndex/>
        </#list>
    </ul>
</li>
</#macro>

<#macro operationTemplate operation endpoint apiIndex operationIndex resourceIndex>
    <#assign pathWithoutFormat=endpoint.getPath()?replace(".{format}","")/>
    <#assign lowercaseHttpMethd = operation.getHttpMethod()?lower_case/>

<li class='${lowercaseHttpMethd} operation' id='${operation.getHttpMethod()}_${operationIndex}'>
    <div class='heading'>
        <h3>
<span class='http_method'>
    <a href='#!/${pathWithoutFormat}/${operation.getNickname()}_${operation.getHttpMethod()}'>${operation.getHttpMethod()}</a>
</span>
<span class='path'>
<a href='#!/${pathWithoutFormat}/${operation.getNickname()}_${operation.getHttpMethod()}'
   onclick="toggle('#content_${apiIndex}_${resourceIndex}_${operationIndex}')">${pathWithoutFormat}</a>
</span>
        </h3>
        <ul class='options'>
            <li>
            ${operation.getSummary()}
            </li>
        </ul>
    </div>
    <div class='content resource${resourceIndex}' id='content_${apiIndex}_${resourceIndex}_${operationIndex}'
         style='display:none'>
        <#attempt>
            <h4>Implementation Notes</h4>

            <p>${operation.getNotes()}</p>
            <#recover>
        </#attempt>

        <#attempt>
            <#assign parameterList = operation.getParameters() />

            <form accept-charset='UTF-8' action='#' class='sandbox'
                  id='${operation.getHttpMethod()}_${operationIndex}_form'
                  method='post'>
                <div style='margin:0;padding:0;display:inline'></div>
                <h4>Parameters</h4>
                <table class='fullwidth'>
                    <thead>
                    <tr>
                        <th>Parameter</th>
                        <th id='${operation.getHttpMethod()}_${operationIndex}_value_header'>
                            Required
                        </th>
                        <th>Description</th>
                    </tr>
                    </thead>
                    <tbody id='${operation.getHttpMethod()}_${operationIndex}_params'>
                        <#list parameterList as parameter>
                        <@paramTemplate param = parameter/>
                    </#list>
                    </tbody>
                </table>
            </form>
            <div class='response' id='${operation.getHttpMethod()}_${operationIndex}_content_sandbox_response'
                 style='display:none'>
                <h4>Request URL</h4>

                <div class='block request_url'></div>
                <h4>Response Body</h4>

                <div class='block response_body'></div>
                <h4>Response Code</h4>

                <div class='block response_code'></div>
                <h4>Response Headers</h4>

                <div class='block response_headers'></div>
            </div>
            <#recover>
        </#attempt>
    </div>
</li>
</#macro>

<#macro paramTemplate param>
    <#attempt>
        <#attempt>
            <#assign paramName = param.getName() />
            <#recover>
                <#assign paramName = param.getNickname() />
        </#attempt>
    <tr>
        <td class='code'> ${paramName}</td>
        <td><#attempt>
            <#if param.getRequired()>
                Required
            <#else>
                Optional
            </#if>
            <#recover>
        </#attempt></td>
        <td width='500'>${param.getDescription()}</td>
    </tr>
        <#recover>
    </#attempt>
</#macro>

<p id='colophon'>
    Sexy API documentation from
    <a href="http://swagger.wordnik.com">Swagger</a>.
</p>
</body>
</html>

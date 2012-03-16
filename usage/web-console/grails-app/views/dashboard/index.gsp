<%-- DO NOT MODIFY THIS FILE, IT IS AUTOMATICALLY GENERATED. INSTEAD MODIFY index.haml --%>
<!DOCTYPE html>
<html>
  <head>
    <title>Brooklyn Web Console - Dashboard</title>
    <meta content='brooklyn' name='layout' />
    <script src='http://maps.google.com/maps/api/js?sensor=false' type='text/javascript'></script>
    <g:javascript src='console/main.js'></g:javascript>
    <g:javascript src='console/util/brooklyn-util.js'></g:javascript>
    <g:javascript src='console/util/gmaps.js'></g:javascript>
    <g:javascript src='console/util/datatable-pagination.js'></g:javascript>
    <g:javascript src='console/dashboard/widget-dashboard.js'></g:javascript>
  </head>
  <body style='height:100%;width:100%;'>
    <div class='ui-tabs ui-widget ui-widget-content ui-corner-all'>
      <div class='brooklyn-header'></div>
      <div>
        <ul class='ui-tabs-nav ui-helper-reset ui-helper-clearfix ui-widget-header ui-corner-all' style='padding:1.2em 0em 0em 0em'>
          <li class='ui-state-default ui-corner-top ui-tabs-selected ui-state-active'>
            <a href='../dashboard/'>Dashboard</a>
          </li>
          <li class='ui-state-default ui-corner-top'>
            <a href='../detail/'>Detail</a>
          </li>
        </ul>
      </div>
    </div>
    <div id='dashboard'>
      <div class='ui-layout-center'>
        <g:render template='dashboard'></g:render>
      </div>
    </div>
  </body>
</html>
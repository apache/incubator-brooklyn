<%-- DO NOT MODIFY THIS FILE, IT IS AUTOMATICALLY GENERATED. INSTEAD MODIFY index.haml --%>
<!DOCTYPE html>
<html>
  <head>
    <title>Brooklyn Web Console - Dashboard</title>
    <meta content='basic' name='layout' />
    <script src='http://maps.google.com/maps/api/js?sensor=false' type='text/javascript'></script>
    <g:javascript src='console/main.js'></g:javascript>
    <g:javascript src='console/jsTree.js'></g:javascript>
    <g:javascript src='console/tabs.js'></g:javascript>
    <g:javascript src='console/summary.js'></g:javascript>
    <g:javascript src='console/effectors.js'></g:javascript>
    <g:javascript src='console/activity.js'></g:javascript>
    <g:javascript src='console/sensors.js'></g:javascript>
    <g:javascript src='console/location.js'></g:javascript>
    <g:javascript src='jquery/jquery.corner.js'></g:javascript>
    <g:javascript src='jquery/jquery.jstree.js'></g:javascript>
  </head>
  <body>
    <div id='layout-middle-content'>
      <div class='layout-left-column'>
        <div id='brooklyn-logo'>
          <h1 id='brooklyn-header'></h1>
        </div>
        <div class='tree-components'>
          <div class='search-box'>
            <input autosave='brooklynEntity' class='search-input' id='search-input' name='name' placeholder='Search' results='5' type='search' />
          </div>
          <div class='jstree' id='jstree'></div>
        </div>
      </div>
      <div class='layout-main-content'>
        <div align='left' id='navigation'></div>
        <div id='tabs'>
          <ul>
            <li>
              <a href='#summary'>Summary</a>
            </li>
            <li>
              <a href='#sensors'>Sensors</a>
            </li>
            <li>
              <a href='#effectors'>Effectors</a>
            </li>
            <li>
              <a href='#activity'>Activity</a>
            </li>
            <li>
              <a href='#location'>Location</a>
            </li>
            <li>
              <a href='#health'>Health</a>
            </li>
            <li>
              <a href='#structure'>Structure</a>
            </li>
          </ul>
          <div id='summary'>
            <div id='summary-basic-info'></div>
            <div id='summary-status'></div>
            <div id='summary-groups'></div>
            <div id='summary-activity'></div>
          </div>
          <div id='sensors'>
            <h2>Sensors</h2>
            <div class='tab-content-full-size'>
              <table class='tab-content-table' id='sensor-data'></table>
            </div>
          </div>
          <div id='effectors'>
            <h2>Effectors</h2>
            <g:render template='/tabs/effectors'></g:render>
          </div>
          <div id='location'>
            <h2>Location</h2>
            <g:render template='/tabs/location'></g:render>
          </div>
          <div id='activity'>
            <h2>Activity</h2>
            <g:render template='/tabs/activity'></g:render>
          </div>
          <div id='health'>
            <h2>Health</h2>
          </div>
          <div id='structure'>
            <h2>Structure</h2>
          </div>
          <p class='bottom-filler'>&nbsp;</p>
        </div>
        <div id='status'>Last Update: <span id="status-message">No data yet.</span></div>
      </div>
    </div>
  </body>
</html>
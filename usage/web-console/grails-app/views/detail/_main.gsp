<%-- DO NOT MODIFY THIS FILE, IT IS AUTOMATICALLY GENERATED. INSTEAD MODIFY _main.haml --%>
<div align='left' id='navigation'></div>
<div id='subtabs'>
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
      <a href='#policies'>Policies</a>
    </li>
  </ul>
  <div id='summary'>
    <div id='summary-basic-info'></div>
    <div id='summary-status'></div>
    <div id='summary-groups'></div>
    <div id='summary-activity'></div>
  </div>
  <div id='sensors' tabindex='1'>
    <div class='sensor-table'>
      <table class='tab-content-table' id='sensor-data'></table>
    </div>
    <div class='sensor-bottom-buttons'>
      <a href='javascript:Brooklyn.sensors.updateSensors();'>RELOAD</a>
      <a href='javascript:Brooklyn.sensors.toggleShowEmptySensors();'>FILTER</a>
    </div>
  </div>
  <div id='effectors' tabindex='2'>
    <g:render template='detail-tabs/effectors'></g:render>
  </div>
  <div id='location' tabindex='3'>
    <g:render template='detail-tabs/location'></g:render>
  </div>
  <div id='activity' tabindex='4'>
    <g:render template='detail-tabs/activity'></g:render>
  </div>
  <div id='policies' tabindex='5'>
    <g:render template='detail-tabs/policies'></g:render>
  </div>
  <p class='bottom-filler'>&nbsp;</p>
</div>
<div id='status'>Last update: <span id="status-message">No data yet.</span></div>
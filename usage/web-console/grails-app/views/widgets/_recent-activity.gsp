<%-- DO NOT MODIFY THIS FILE, IT IS AUTOMATICALLY GENERATED. INSTEAD MODIFY _recent-activity.haml --%>
<div class='portlet'>
  <div class='portlet-header'>
    Recent Activity
  </div>
  <div class='portlet-content' style='overflow:hidden !important;'>
    <g:javascript src='console/dashboard/widgets/recent-activity.js'></g:javascript>
    <!-- * Paginate code needs to go somewhere. Remote Pagination with JSON. */ -->
    <g:paginate controller="entity" action="allActivity" total="5" update="#recent-activity-table" />
    <div id='activity-table-container'>
      <table id='recent-activity-table'></table>
      <div align='right' id='auto-refesh-container'>
        Auto Update:
        <input checked='checked' id='updateCheck' type='checkbox' />
      </div>
    </div>
  </div>
</div>
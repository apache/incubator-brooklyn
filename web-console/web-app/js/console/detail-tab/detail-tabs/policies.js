Brooklyn.policies = (function(){
    
    var tableId = '#policies-data';
    var aoColumns = [   {"mDataProp": "displayName", "sTitle": "Policy Name", "sWidth": "65%"},
                        {"mDataProp": "policyStatus", "sTitle": "Status", "sWidth": "35%"}];

    function PoliciesTab() {
        this.id = 'policies';
        this.update = function() {
            if(typeof this.entity_id !== 'undefined'){
                $.getJSON("../entity/policies?id=" + this.entity_id, updatePoliciesTable).error(
                    function() {
                        $(Brooklyn.eventBus).trigger('update_failed', "Policy view could not get policies");
                    });
            }
        }
        this.makeHandlers();
    }

    PoliciesTab.prototype = new Brooklyn.tabs.Tab();

    function updatePolicySelection(event){
    //when user selects policy from grid
    }

    function updatePoliciesTable(policies){
        Brooklyn.util.getDataTable(tableId, '.', aoColumns, updatePolicySelection, policies);
    }

    function policiesTabSelected(e,id){
    }

    function executeAction(event){
        if(confirm("Are you sure you wish to execute?")){
            alert("YOUVE EXECUTED");
        }
    }

    function init(){
        var policiesTab = new PoliciesTab();
        $('#policy-action-execution-button').click(executeAction);
        $(Brooklyn.eventBus).bind("entity_selected", policiesTab.handler.entitySelected);
        $(Brooklyn.eventBus).bind("tab_selected", policiesTab.handler.tabSelected);
        $(Brooklyn.eventBus).bind("tab_selected", policiesTabSelected);

    }


    return {
        init : init , executeAction : executeAction
    }

})();

$(document).ready(Brooklyn.policies.init);

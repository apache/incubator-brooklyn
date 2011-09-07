Brooklyn.policies = (function(){
    var policyName;
    var policyDescription;
    var policyId;
    var tableId = '#policies-data';
    var aoColumns = [   {"mDataProp": "displayName", "sTitle": "Policy Name", "sWidth": "65%"},
                        {"mDataProp": "policyStatus", "sTitle": "Status", "sWidth": "35%"}];
    var appPolicies;
    var selectedEntityId;

    function PoliciesTab() {
        this.id = 'policies';
        this.update = function() {
            if(typeof this.entity_id !== 'undefined'){
                $.getJSON("../entity/policies?id=" + this.entity_id, updatePoliciesTable).error(
                    function() {
                        $(Brooklyn.eventBus).trigger('update_failed', "Policy view could not get policies");
                    });
                selectedEntityId = this.entity_id;
            }
        }
        this.makeHandlers();
    }

    PoliciesTab.prototype = new Brooklyn.tabs.Tab();

    function updatePolicySelection(event){
        //when user selects policy from grid
        reset();
        $(event.target.parentNode).addClass('row_selected');
        document.getElementById('policyAction').disabled = false;
        var result = Brooklyn.util.getDataTableSelectedRowData(tableId, event);
        policyName = result.displayName;
        policyDescription = result.description;
        policyId = result.id;
        $('#policyName').empty();
        var nameText = document.createElement("p");
        if(policyName!=null){
            nameText.textContent = policyName;
        }
        else{
            nameText.textContent = 'The policy has no name';
        }
        $('#policyName').append(nameText);
        $('#policyDescription').empty();
        var descriptionText = document.createElement("p");
        if(policyDescription!=null){
            descriptionText.textContent = policyDescription;
        }
        else{
            descriptionText.textContent = 'This policy has no description';
        }
        $('#policyDescription').append(descriptionText);
    }
    function reset() {
        var settings = Brooklyn.util.getDataTable(tableId).fnSettings().aoData;
        for(var row in settings) {
            $(settings[row].nTr).removeClass('row_selected');
        }
    }


    function updatePoliciesTable(policies){
        Brooklyn.util.getDataTable(tableId, '.', aoColumns, updatePolicySelection, policies);
    }

    function policiesTabSelected(e,id){
    }

    function executeAction(event){
        var chosenAction = document.getElementById('policyAction').value;
        if(chosenAction=='default'){ alert('You must choose an action to execute!'); }
        else{
            if(confirm("Are you sure you wish to "+chosenAction+" this policy?")){
                var dataMap = new Object();
                dataMap["entityId"] = selectedEntityId;
                dataMap["policyId"] = policyId;
                dataMap["chosenAction"] = chosenAction;
                $.ajax({
                    url: "../entity/execute",
                    data: dataMap,
                    success: function(){
                        alert('Policy: "' + policyId + '" has had action ' + chosenAction + ' executed upon it');
                        //reset UI components now.
                    }
                });

            }
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

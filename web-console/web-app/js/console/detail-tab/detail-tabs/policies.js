Brooklyn.policies = (function(){
    
    var tableId = '#policies-data';
    var aoColumns = [   {"mDataProp": "displayName", "sTitle": "Policy Name", "sWidth": "65%"},
                        {"mDataProp": "policyStatus", "sTitle": "Status", "sWidth": "35%"}];
    var appPolicies;

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
        reset();
        $(event.target.parentNode).addClass('row_selected');
        document.getElementById('policyAction').disabled = false;
        var result = Brooklyn.util.getDataTableSelectedRowData(tableId, event);
        var name = result.displayName;
        var description = result.description;
        $('#policyName').empty();
        var nameText = document.createElement("p");
        if(name!=null){
            nameText.textContent = name;
        }
        else{
            nameText.textContent = 'The policy has no name';
        }
        $('#policyName').append(nameText);
        $('#policyDescription').empty();
        var descriptionText = document.createElement("p");
        if(description!=null){
            descriptionText.textContent = description;
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
                alert("YOUVE EXECUTED");
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

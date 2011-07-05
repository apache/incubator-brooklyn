Brooklyn.effectors = (function(parent) {

    function _getEffectors(e, entity_id, fn) {
        $.ajax({
            url: Brooklyn.urls.effectors.getEffectors,
            data: "id="+entity_id,
            success: function(result){
                fn(result)
            }
        });
    }

    function updateEffectorsList(e, entity_id){
        $('#effectorList').find('option').remove().end()
        var effectors = _getEffectors(e, entity_id, function(result){
            for(i=0;i<result.length;i++){
                var option = document.createElement("option");
                option.text = result[i].name;
                option.onclick = Brooklyn.effectors.updateParameters;
                $('#effectorList').get(0)[$('#effectorList option').length] = option;
            }
        });
    }

    function updateParameters(){
        //TODO update parameter panel
        console.log("Updated Parameters")
    }

    return {
        updateEffectorsList: updateEffectorsList,
        updateParameters: updateParameters
    };

}(Brooklyn || {}));

$(document).ready(function(){
    $(Brooklyn.eventBus).bind("entity_selected", Brooklyn.effectors.updateEffectorsList);
});
Brooklyn.effectors = (function() {

    function updateEffectorsList(json) {
        $('#effectorList').find('option').remove().end()

        for (name in json) {
            option = document.createElement("option");
            option.text = name;
            option.value = name;
            $('#effectorList').get(0)[$('#effectorList option').length] = option;
        }

        $('#effectorList').change(updateParameters);

        updateParameters();
    }

    function updateParameters(){
        //TODO update parameter panel
        if ($('#effectorList option:selected').length == 0) {
            $('#effector-input1-label').html("Nothing!");
        } else {
            var option = $('#effectorList option:selected')[0];
            $('#effector-input1-label').html("GOT " + option.text);
        }
    }

    function updateList(e, entity_id) {
        if (typeof entity_id === 'undefined') {
            return;
        }
        // TODO: Handle failure
        $.getJSON("effectors?id=" + entity_id, updateEffectorsList);
    }

    function init() {
        $(Brooklyn.eventBus).bind("entity_selected", updateList);
    }

    return {init: init};
})();

$(document).ready(Brooklyn.effectors.init);

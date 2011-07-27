 $(document).ready(function () {
     <!-- CREATE TWO LAYOUT OBJECTS AND THEN GIVE THEM SETTINGS WHICH ARE DEFINED BELOW -->
        outerLayout=$("#test-body").layout( outerLayoutSettings );
        var resizeTree = outerLayout.options.west.resizable;
        resizeTree=true;
    });

var outerLayoutSettings = {
                name: "headerFooterSettings",
                defaults:{
                applyDefaultStyles:   true,
                closable:             false,
                initClosed:           false,
                spacing_open: 0
                },
                north:{


                },
                south:{

                },
                west:{


                },
                east:{

                },
                center:{}

    }


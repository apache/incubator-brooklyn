   /*
    * THIS SCRIPT FILE DEFINES LAYOUT FOR THE PAGE AND CONTAINER BEHAVIOUR WHEN USING JQUERY UI LAYOUT CONTAINERS
    *
    * So when the page loads we set the layouts for both the seperate containers.
    * one layout variable will be for header and footer and the other for the inner container.
    *
    */
    var outerLayout, innerLayout;

    $(document).ready(function () {
     <!-- CREATE TWO LAYOUT OBJECTS AND THEN GIVE THEM SETTINGS WHICH ARE DEFINED BELOW -->
        outerLayout=$("body").layout( outerLayoutSettings );
        innerLayout=$("body > .ui-layout-center").layout( innerLayoutSettings );
        innerWestLayout=$("body > .ui-layout-center > .ui-layout-west").layout(leftSideLayoutSettings);
        var resizeTree = innerLayout.options.west.resizable;
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

    var innerLayoutSettings = {
                 name: "headerFooterSettings",
                 defaults:{
                 applyDefaultStyles:  true,
                 closable:             false,
                 initClosed:           false,

                 },
                 north:{


                 },
                 south:{

                 },
                 west:{
                 closable: true,
                 size: 250

                 },
                 east:{

                 },
                 center:{}

    }

    var leftSideLayoutSettings = {
                 name: "headerFooterSettings",
                 defaults:{
                       applyDefaultStyles:true
                 },
                 north:{

                 },
                 south:{

                 },
                 west:{

                 },
                 east:{

                 },
                 center:{   }

    }

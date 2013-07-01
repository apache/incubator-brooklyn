({
    // The entry point to the application. Brooklyn's is in config.js.
    name: "config",
    baseUrl: "${project.basedir}/src/main/webapp/assets/js",
    mainConfigFile: "${project.basedir}/src/main/webapp/assets/js/config.js",
    paths: {
        // Include paths to external resources (e.g. on a CDN) here.

        // Optimiser looks for js/requireLib.js by default.
        "requireLib": "libs/require"
    },

    // Place the optimised file in target/<war>/assets.
    out: "${project.build.webapp}/assets/js/gui.min.js",

    // Set to "none" to skip minification
    optimize: "uglify"
})

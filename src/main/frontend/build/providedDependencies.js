const providedDependencies = new Map();

providedDependencies.set('jquery', {
    dependency: 'jira.webresources:jquery',
    import: {
        var: `require('jquery')`,
        amd: 'jquery',
    },
});

providedDependencies.set('lodash', {
    dependency: 'com.atlassian.plugin.jslibs:underscore-1.4.4',
    import: {
        var: `require('atlassian/libs/underscore-1.4.4')`,
        amd: 'atlassian/libs/underscore-1.4.4',
    },
});

providedDependencies.set('macro-params-serializer', {
  dependency: 'confluence.editor.actions:editor-macro-browser',
  //dependency: 'com.atlassian.confluence.plugins.confluence-macro-browser:macro-browser-js',
  import: {
    var: `require('confluence-macro-browser/macro-parameter-serializer')`,
    amd: 'confluence-macro-browser/macro-parameter-serializer',
  }
});

providedDependencies.set('user-group-select2', {
  dependency: 'com.atlassian.confluence.plugins.confluence-ui-components:user-group-select2',
  import: {
    var: `require('confluence-ui-components/js/user-group-select2')`,
    amd: 'confluence-ui-components/js/user-group-select2',
  }
});

module.exports = providedDependencies;

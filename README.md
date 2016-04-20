# Zabbix Plugin
Zabbix Plugin for Atlassian Confluence is used to add Zabbix charts to your Confluence wiki.

There are many of us sysadmins out there that use Confluence as a primary source of information about
their hardware and software -- the type of information that would probably contain installation instructions,
configuration files, performance reports and other valuable bits and pieces of information. This is usually
accompanied by network monitoring tools such as Nagios or Zabbix. This plugin is used to add Zabbix charts
(or graphs) to your Confluence page to illustrate your configuration details and reports with online visual
information.

## Using the plugin

Add "confluence" account in a "Confluence" user group to your Zabbix server. Grant the group read-only
permissions to whatever hosts you choose to be available to your Confluence server. <br/>
Install the plugin. Open plugin configuration page and setup Zabbix-connection using your Zabbix URL,
username (confluence) and password. Then drop zabbix plugin to a page and set graph parameters: id, period
in seconds, and width in pixels. You can find graph id in Zabbix by going to Monitoring -> Graphs, then
selecting a host and a graph. The graph id would appear in your browser URL, for example:

```
http://zabbix.sample.com/zabbix/charts.php?sid=…&form_refresh=1&fullscreen=0&groupid=0&hostid=10084&graphid=661
```

Add graph replacement text to the body of the plugin, for example, CPU Load. The text would be displayed
when your Zabbix server is not available.

## License

This plugin is licensed under the terms of the MIT license. All source code is publicly available at
https://github.com/mesilat/zabbix-plugin and it will remain there. It was tested with Zabbix versions 2 and 3.
No support is provided for this plugin. If you wish to have a supported commercial version please contact me at
admin@mesilat.com

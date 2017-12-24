package com.mesilat.format;

import com.mesilat.zabbix.client.ZabbixItem;

public class ZabbixIconComponent implements FormatComponent {
    private static final String ICON_HREF = "/download/resources/com.mesilat.zabbix-plugin/images/pluginIcon.png";

    private final String baseUrl;
    
    @Override
    public String format(ZabbixItem item) {
        return "<img src='" + baseUrl + ICON_HREF + "'/>";
    }

    public ZabbixIconComponent(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
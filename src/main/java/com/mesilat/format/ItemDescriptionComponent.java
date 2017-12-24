package com.mesilat.format;

import com.mesilat.zabbix.client.ZabbixItem;

public class ItemDescriptionComponent implements FormatComponent {
    @Override
    public String format(ZabbixItem item) {
        return item.getDescription();
    }
}
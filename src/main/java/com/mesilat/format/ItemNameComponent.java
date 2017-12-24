package com.mesilat.format;

import com.mesilat.zabbix.client.ZabbixItem;

public class ItemNameComponent implements FormatComponent {
    @Override
    public String format(ZabbixItem item) {
        return item.getName();
    }
}
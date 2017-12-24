package com.mesilat.format;

import com.mesilat.zabbix.client.ZabbixItem;

public class ItemIdComponent implements FormatComponent {
    @Override
    public String format(ZabbixItem item) {
        return item.getItemId().toString();
    }
}
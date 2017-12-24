package com.mesilat.format;

import com.mesilat.zabbix.client.ZabbixItem;

public class ItemKeyComponent implements FormatComponent {
    @Override
    public String format(ZabbixItem item) {
        return item.getKey();
    }
}
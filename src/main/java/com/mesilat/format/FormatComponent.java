package com.mesilat.format;

import com.mesilat.zabbix.client.ZabbixItem;

public interface FormatComponent {
    String format(ZabbixItem item);
}
package com.mesilat.format;

import com.mesilat.zabbix.client.ZabbixItem;

public class TextComponent implements FormatComponent {
    private final String text;

    @Override
    public String format(ZabbixItem item) {
        return text;
    }

    public TextComponent(String text) {
        this.text = text;
    }
}
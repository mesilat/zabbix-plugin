package com.mesilat.format;

import com.mesilat.zabbix.client.ZabbixItem;
import java.util.Locale;
import org.joda.time.format.DateTimeFormat;

public class ItemClockComponent implements FormatComponent {
    private final Locale locale;
    private final String pattern;

    @Override
    public String format(ZabbixItem item) {
        String pat = (pattern == null)? DateTimeFormat.patternForStyle("SS", locale): pattern;
        return DateTimeFormat.forPattern(pat).print(item.getClock().getTime());
    }

    public ItemClockComponent(Locale locale, String pattern) {
        this.locale = locale;
        this.pattern = pattern;
    }
    public ItemClockComponent(Locale locale) {
        this(locale, null);
    }
}
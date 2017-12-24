package com.mesilat.format;

import com.mesilat.zabbix.client.ZabbixItem;
import java.text.DecimalFormat;
import java.text.Format;
import java.util.Locale;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;
import org.joda.time.format.PeriodFormatter;

public class ItemValueComponent implements FormatComponent {
    private static final DecimalFormat DEFAULT_NUMBER_FORMAT = new DecimalFormat("###,###,###,##0.00");
    
    private static final long GB = 1024 * 1024 * 1024;
    private static final long MB = 1024 * 1024;
    private static final long KB = 1024;

    private final Locale locale;
    private final String valueFormat;

    @Override
    public String format(ZabbixItem item) {
        switch (item.getValueType()) {
            case 0: // - numeric float
                break;
            case 1: // - character
                return item.getValue();
            case 2: // - log
                return item.getValue();
            case 3: // - numeric unsigned
                break;
            case 4: // - text
                return item.getValue();
        }
        switch (item.getDataType()) {
            case 0: // - (default) decimal
                break;
            case 1: // - octal
                return item.getValue();
            case 2: // - hexadecimal
                return item.getValue();
            case 3: // - boolean
                return item.getValue();
        }
        if ("B".equals(item.getUnits()) || "Bps".equals(item.getUnits())) { // B for Bytes, Bps for bytes per second
            if ("GB".equalsIgnoreCase(valueFormat)) {
                return DEFAULT_NUMBER_FORMAT.format(Double.parseDouble(item.getValue()) / GB);
            } else if ("MB".equalsIgnoreCase(valueFormat)) {
                return DEFAULT_NUMBER_FORMAT.format(Double.parseDouble(item.getValue()) / MB);
            } else if ("KB".equalsIgnoreCase(valueFormat)) {
                return DEFAULT_NUMBER_FORMAT.format(Double.parseDouble(item.getValue()) / KB);
            }
        } else if ("uptime".equals(item.getUnits()) || "s".equals(item.getUnits())) { // seconds
            PeriodFormatter fmt = PeriodFormat.wordBased(locale);
            Period period = new Period(Long.parseLong(item.getValue()) * 1000);
            return fmt.print(period);
        }
        if (valueFormat != null) {
            Format fmt = new DecimalFormat(valueFormat);
            return fmt.format(Double.parseDouble(item.getValue()));
        } else {
            return item.getValue();
        }
    }

    public ItemValueComponent(Locale locale, String valueFormat) {
        this.locale = locale;
        this.valueFormat = valueFormat;
    }
    public ItemValueComponent(Locale locale) {
        this(locale, null);
    }
}
package com.mesilat.format;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.sal.api.user.UserKey;
import com.mesilat.zabbix.client.ZabbixItem;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.lang.StringEscapeUtils;

public class CompiledFormat {
    private final List<FormatComponent> formatComponents;

    public String format(ZabbixItem item) {
        boolean nospace = true;
        StringBuilder sb = new StringBuilder();
        for (FormatComponent fc : formatComponents) {
            if (nospace) {
                nospace = false;
            } else if (fc instanceof TextComponent) {
                nospace = true;
            } else {
                sb.append(" ");
            }
            if (fc instanceof ZabbixIconComponent) {
                sb.append(fc.format(item));
            } else {
                sb.append(StringEscapeUtils.escapeHtml(fc.format(item)));
            }
        }
        return sb.toString();
    }

    private CompiledFormat() {
        formatComponents = new ArrayList<>();
    }

    public static CompiledFormat compile(String format, String baseUrl, Locale locale) throws ParseException {
        CompiledFormat cf = new CompiledFormat();
        for (int i = 0; i < format.length(); i++) {
            switch (format.charAt(i)) {
                case 'v': // Last Value
                    if (format.length() > i + 1 && format.charAt(i + 1) == '{') {
                        int j = format.indexOf('}', i + 2);
                        if (j < 0) {
                            throw new ParseException("\"{\" does not have a matching \"}\"", i + 1);
                        }
                        cf.formatComponents.add(new ItemValueComponent(locale, format.substring(i + 2, j)));
                        i = j;
                    } else {
                        cf.formatComponents.add(new ItemValueComponent(locale));
                    }
                    break;
                case 'c': // Last Clock
                    if (format.length() > i + 1 && format.charAt(i + 1) == '{') {
                        int j = format.indexOf('}', i + 2);
                        if (j < 0) {
                            throw new ParseException("\"{\" does not have a matching \"}\"", i + 1);
                        }
                        cf.formatComponents.add(new ItemClockComponent(locale, format.substring(i + 2, j)));
                        i = j;
                    } else {
                        cf.formatComponents.add(new ItemClockComponent(locale));
                    }
                    break;
                case 'n': // Name
                    cf.formatComponents.add(new ItemNameComponent());
                    break;
                case 'k': // Key
                    cf.formatComponents.add(new ItemKeyComponent());
                    break;
                case 'd': // Description
                    cf.formatComponents.add(new ItemDescriptionComponent());
                    break;
                case 'u': // Units
                    cf.formatComponents.add(new ItemUnitsComponent());
                    break;
                case 'i': // Item Id
                    cf.formatComponents.add(new ItemIdComponent());
                    break;
                case 't': // Data Type
                    break;
                case 'z': // Zabbix icon
                    cf.formatComponents.add(new ZabbixIconComponent(baseUrl));
                    break;
                case '\'': // Plain text
                    StringBuilder t = new StringBuilder();
                    for (int j = i + 1; j < format.length(); j++) {
                        if (format.charAt(j) == '\'') {
                            if (format.length() > j + 1 && format.charAt(j + 1) == '\'') {
                                t.append("\'");
                                j++;
                            } else {
                                i = j;
                                break;
                            }
                        } else {
                            t.append(format.charAt(j));
                        }
                    }
                    cf.formatComponents.add(new TextComponent(t.toString()));
                    break;
                case '\"':
                    t = new StringBuilder();
                    for (int j = i + 1; j < format.length(); j++) {
                        if (format.charAt(j) == '\"') {
                            if (format.length() > j + 1 && format.charAt(j + 1) == '\"') {
                                t.append("\"");
                                j++;
                            } else {
                                i = j;
                                break;
                            }
                        } else {
                            t.append(format.charAt(j));
                        }
                    }
                    cf.formatComponents.add(new TextComponent(t.toString()));
                    break;
                default:
                    throw new ParseException("Invalid formatting option: " + format.charAt(i), i);
            }
        }
        return cf;
    }
    public static String getFormat(ActiveObjects ao, UserKey userKey, String name){
        return ao.executeInTransaction(()->{
            ItemFormat[] formats = ao.find(ItemFormat.class, "NAME = ? AND OWNER_KEY = ?", name, userKey.getStringValue());
            if (formats.length > 0){
                return formats[0].getFormat();
            }
            formats = ao.find(ItemFormat.class, "NAME = ? AND OWNER_KEY IS NULL", name);
            if (formats.length > 0){
                return formats[0].getFormat();
            }
            return name;
        });
    }
    public static boolean isValidFormat(String format){
        try {
            CompiledFormat cf = compile(format, "/", Locale.forLanguageTag("en_US"));
            return true;
        } catch (ParseException ex) {
            return false;
        }
    }
}
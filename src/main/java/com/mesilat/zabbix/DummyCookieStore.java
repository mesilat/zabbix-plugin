package com.mesilat.zabbix;

import java.util.ArrayList;
import org.apache.http.client.CookieStore;
import java.util.Date;
import java.util.List;
import org.apache.http.cookie.Cookie;

public class DummyCookieStore implements CookieStore {
    @Override
    public void addCookie(Cookie cookie) {
    }
    @Override
    public List<Cookie> getCookies() {
        return new ArrayList<>();
    }
    @Override
    public boolean clearExpired(Date date) {
        return true;
    }
    @Override
    public void clear() {
    }
}
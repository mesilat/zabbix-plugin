package com.mesilat.zabbix;

import com.atlassian.sal.api.user.UserKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

public class TokenService implements InitializingBean, DisposableBean, Runnable {
    public static final long EXPIRES = 10 * 60 * 1000; // 10 min

    private static TokenService instance;

    private final Map<String,UserAutorization> authorizations;
    private final Thread cleanupThread;

    // <editor-fold defaultstate="collapsed" desc="InitializingBean, DisposableBean, Runnable Implementation">
    @Override
    public void afterPropertiesSet() throws Exception {
        cleanupThread.start();
        instance = this;
    }
    @Override
    public void destroy() throws Exception {
        cleanupThread.interrupt();
        authorizations.clear();
    }
    @Override
    public void run() {
        while(true) {
            try {
                Thread.sleep(10000);
                Date now = new Date(System.currentTimeMillis());
                synchronized(authorizations){
                    List<String> expiredTokens = new ArrayList<>();
                    for (Entry<String,UserAutorization> e : authorizations.entrySet()){
                        if (e.getValue().getExpiration().before(now)){
                            expiredTokens.add(e.getKey());
                        }
                    }
                    for (String token : expiredTokens){
                        authorizations.remove(token);
                    }
                }
            } catch(InterruptedException ignore) {
                break;
            }
        }
    }
    // </editor-fold>}

    public TokenService() {
        authorizations = new HashMap<>();
        cleanupThread = new Thread(this);
    }

    public static boolean isValidToken(UserKey userKey, String token) {
        if (instance == null) {
            return false;
        }

        synchronized(instance.authorizations){
            if (instance.authorizations.containsKey(token)){
                UserAutorization auth = instance.authorizations.remove(token);
                if (auth.getUserKey().equals(userKey)){
                    return true;
                }
            }
        }

        return false;
    }
    public static String createToken(UserKey userKey) {
        if (instance == null) {
            return null;
        } else {
            String token = UUID.randomUUID().toString();
            synchronized(instance.authorizations){
                instance.authorizations.put(token, new UserAutorization(userKey));
            }
            return token;
        }
    }
    
    private static class UserAutorization {
        private final UserKey userKey;
        private final Date expiration;

        public UserKey getUserKey() {
            return userKey;
        }
        public Date getExpiration() {
            return expiration;
        }

        public UserAutorization(UserKey userKey) {
            this.userKey = userKey;
            this.expiration = new Date(System.currentTimeMillis() + EXPIRES);
        }
    }
}
package com.mesilat.zabbix;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

public class TempFileService implements InitializingBean, DisposableBean, Runnable {
    private static TempFileService instance;

    private final Thread monitoringThread;
    private final List<TempFile> files = new ArrayList<>(100);
    
    // <editor-fold defaultstate="collapsed" desc="InitializingBean, DisposableBean, Runnable Implementation">
    @Override
    public void afterPropertiesSet() throws Exception {
        monitoringThread.start();
        instance = this;
    }
    @Override
    public void destroy() throws Exception {
        monitoringThread.interrupt();
    }
    public void run() {
        try {
            while (true) {
                Thread.sleep(1000);
                synchronized(files) {
                    Date now = new Date(System.currentTimeMillis());
                    List<TempFile> filesToDelete = new ArrayList<TempFile>(100);
                    for (TempFile file : files) {
                        if (file.getTimeToLive().before(now)) {
                            filesToDelete.add(file);
                        }
                    }
                    for (TempFile file : filesToDelete) {
                        files.remove(file);
                        if (file.getPath().exists() && !file.getPath().delete()) {
                            file.getPath().deleteOnExit();
                        }
                    }
                }
            }
        } catch(InterruptedException ignore) {
        }
    }
    // </editor-fold>
    
    public static File createTempFile() throws IOException {
        final SecureRandom random = new SecureRandom();
        final String imageId = new BigInteger(130, random).toString(32);

        File path = new File(System.getProperty("java.io.tmpdir"), imageId + ".png");
        if (instance != null) {
            synchronized(instance.files) {
                instance.files.add(new TempFile(path));
            }
        }
        return path;
    }
    
    public TempFileService() {
        monitoringThread = new Thread(this);
    }
    
    static class TempFile {
        private final File path;
        private final Date timeToLive;

        public File getPath() {
            return path;
        }
        public Date getTimeToLive() {
            return timeToLive;
        }
        
        public TempFile(File path) {
            this.path = path;
            timeToLive = new Date(System.currentTimeMillis() + 600000);
        }
    }
}

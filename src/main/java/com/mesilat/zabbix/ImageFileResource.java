package com.mesilat.zabbix;

import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.seraph.auth.DefaultAuthenticator;
import com.mesilat.util.DemoAuthFilter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/image")
public class ImageFileResource {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.zabbix");

    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response get(@Context HttpServletRequest request) {
        File file = new File(System.getProperty("java.io.tmpdir"), request.getParameter("file"));
        if (waitForFile(file)) {
            try {
                cleanDemoAuth(request);
                return Response
                    .ok(Files.readAllBytes(Paths.get(file.getAbsolutePath())))
                    .build();
            } catch(IOException ex) {
                LOGGER.warn("Error reading file " + file.getAbsolutePath(), ex);
                cleanDemoAuth(request);
                return Response.status(Response.Status.NOT_FOUND).build();
            } finally {
                file.delete();
            }
        } else {
            cleanDemoAuth(request);
            return Response
                .status(Response.Status.NOT_FOUND)
                .build();
        }
    }
    private boolean waitForFile(File file) {
        try {
            for (int i = 0; i < 100; i++) {
                if (file.exists() && file.length() > 0) {
                    break;
                } else {
                    Thread.sleep(100);
                }
            }
            Thread.sleep(100);
        } catch(InterruptedException ignore) {
        }
        return file.exists();
    }
    private void cleanDemoAuth(HttpServletRequest request) {
        if (request.getSession().getAttribute(DefaultAuthenticator.LOGGED_IN_KEY) != null) {
            try {
                ConfluenceUser user = (ConfluenceUser)request.getSession().getAttribute(DefaultAuthenticator.LOGGED_IN_KEY);
                if (user.getKey().equals(DemoAuthFilter.DEMO)) {
                    request.getSession().setAttribute(DefaultAuthenticator.LOGGED_IN_KEY, null);
                }
            } catch(Throwable ignore) {}
        }
    }
}

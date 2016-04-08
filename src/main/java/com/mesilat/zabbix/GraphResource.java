package com.mesilat.zabbix;

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

@Path("/graph")
public class GraphResource {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.zabbix");

    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response get(@Context HttpServletRequest request) {
        String imageId = request.getParameter("id");
        File file = new File("/tmp/" + imageId + ".png");

        try {
            for (int i = 0; i < 100; i++) {
                if (!file.exists()) {
                    Thread.sleep(100);
                } else {
                    break;
                }
            }
            Thread.sleep(100);
        } catch(InterruptedException ignore) {
        }
        
        if (file.exists()) {
            try {
                byte[] buf = Files.readAllBytes(Paths.get(file.getAbsolutePath()));
                return Response.ok(buf).build();
            } catch(IOException ex) {
                LOGGER.warn("Error reading file " + file.getAbsolutePath(), ex);
                return Response.serverError().build();
            } finally {
                file.delete();
            }
        } else {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }    
}

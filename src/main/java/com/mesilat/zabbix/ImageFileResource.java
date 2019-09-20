package com.mesilat.zabbix;

import com.mesilat.zabbix.client.ZabbixClientException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

@Path("/image")
public class ImageFileResource {
    private final ImageService imageService;

    @GET
    @Produces("image/png")
    public Response get(@Context HttpServletRequest request) {
        try {
            if (request.getParameter("server") == null){
                return Response.ok(imageService.getImage(
                        request.getParameter("graph-id"),
                        request.getParameter("width"),
                        request.getParameter("height"),
                        request.getParameter("period")
                )).build();
            } else {
                return Response.ok(imageService.getImage(
                    request.getParameter("server"),
                    request.getParameter("host"),
                    request.getParameter("graph"),
                    request.getParameter("width"),
                    request.getParameter("height"),
                    request.getParameter("period")
                )).build();
            }
        } catch (ZabbixResourceException ex) {
            return Response.status(ex.getStatus()).entity(ex.getMessage()).build();
        } catch (ZabbixClientException ex) {
            return Response.status(ex.getErrorCode()).entity(ex.getMessage()).build();
        }
    }

    @GET
    @Path("/map")
    @Produces("image/png")
    public Response map(@Context HttpServletRequest request) {
        try {
            return Response.ok(imageService.getMapImage(
                request.getParameter("server"),
                request.getParameter("map"),
                request.getParameter("severity")
            )).build();
        } catch (ZabbixResourceException ex) {
            return Response.status(ex.getStatus()).entity(ex.getMessage()).build();
        } catch (ZabbixClientException ex) {
            return Response.status(ex.getErrorCode()).entity(ex.getMessage()).build();
        }
    }

    @GET
    @Path("/map-svg")
    @Produces("application/json")
    public Response mapSvg(@Context HttpServletRequest request) {
        try {
            return Response.ok(imageService.getMapImage(
                request.getParameter("server"),
                request.getParameter("map"),
                request.getParameter("severity")
            )).build();
        } catch (ZabbixResourceException ex) {
            return Response.status(ex.getStatus()).entity(ex.getMessage()).build();
        } catch (ZabbixClientException ex) {
            return Response.status(ex.getErrorCode()).entity(ex.getMessage()).build();
        }
    }

    public ImageFileResource(ImageService imageService) {
        this.imageService = imageService;
    }
}
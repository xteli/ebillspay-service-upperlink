/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ebillspay.payment.utility.util;

import com.ebillspay.payment.lib.util.CORSFilter;
import com.ebillspay.payment.utility.service.UtilityService;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.ext.RuntimeDelegate;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.server.ResourceConfig;

/**
 *
 * @author chineduojiteli
 */
public class StartupUtil {

    private ResourceConfig config;
    private HttpServer endpointServer;

    public boolean isPublished() {
        return (endpointServer != null && endpointServer.isStarted());
    }

    public HttpServer publish(int port) {
        if (endpointServer == null) {
            config = new ResourceConfig();
            config.register(CORSFilter.class);
            config.register(UtilityService.class);
            HttpHandler handler = RuntimeDelegate.getInstance().createEndpoint(config, HttpHandler.class);
            endpointServer = HttpServer.createSimpleServer(null, port);
            endpointServer.getServerConfiguration().addHttpHandler(handler);
            try {
                //start server
                endpointServer.start();
                //keeping the server running
                Thread.currentThread().join();
            } catch (Exception ex) {
                Logger.getLogger(StartupUtil.class.getName())
                        .log(Level.SEVERE, "Restful Endpoint For eBillspay Service Publishing Failed : ", ex);
            }
        }
        if (!isPublished()) {
            stop();
        }
        return endpointServer;
    }

    public void stop() {
        if (endpointServer != null) {
            endpointServer.stop();
            endpointServer = null;
        }
    }

    public void destroy() {
        stop();
        endpointServer = null;
    }
}


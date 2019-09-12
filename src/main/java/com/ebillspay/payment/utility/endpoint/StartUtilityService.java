/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ebillspay.payment.utility.endpoint;

import com.ebillspay.payment.utility.service.UtilityService;
import com.ebillspay.payment.utility.util.StartupUtil;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.http.server.HttpServer;

/**
 *
 * @author chineduojiteli
 */
public class StartUtilityService {

    static HttpServer serviceEntryPoint = null;
    StartupUtil startup = new StartupUtil();

    public StartUtilityService(int port) {
        initService(port);
    }

    private void initService(int port) {
        try {
            serviceEntryPoint = startup.publish(port);
        } catch (Exception ex) {
            if (serviceEntryPoint != null) {
                serviceEntryPoint.stop();
            }
            Logger.getLogger(UtilityService.class.getName())
                    .log(Level.SEVERE, "Web Service Monitor(Lazy) Failed: Falling back to Eager Mode", ex);
        }
    }

    private void endServices() {
        if (serviceEntryPoint != null) {
            startup.destroy();
        }
    }

    public static void main(String[] args) {
       //  final StartUtilityService start = new StartUtilityService(9119);
         final StartUtilityService start = new StartUtilityService(Integer.parseInt(args[0]));

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                start.endServices();
            }
        });
    }
}

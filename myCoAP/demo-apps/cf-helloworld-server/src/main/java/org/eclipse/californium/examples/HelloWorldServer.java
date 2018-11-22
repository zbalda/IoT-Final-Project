/*******************************************************************************
 * Copyright (c) 2015 Institute for Pervasive Computing, ETH Zurich and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *    Matthias Kovatsch - creator and main architect
 *    Kai Hudalla (Bosch Software Innovations GmbH) - add endpoints for all IP addresses
 ******************************************************************************/
package org.eclipse.californium.examples;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.EndpointManager;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.server.resources.CoapExchange;

// temperature sensor
import com.pi4j.io.gpio.*;
import com.pi4j.component.temperature.TemperatureSensor;
import com.pi4j.io.w1.W1Master;
import com.pi4j.temperature.TemperatureScale;


public class HelloWorldServer extends CoapServer {
	
	// GPIO variables
	public static GpioController gpio;

	// Sensor variables
	public static W1Master w1Master;
	public static TemperatureSensor tempSensor;

	private static final int COAP_PORT = NetworkConfig.getStandard().getInt(NetworkConfig.Keys.COAP_PORT);
    /*
     * Application entry point.
     */
    public static void main(String[] args) {
        
        try {

            // create server
            HelloWorldServer server = new HelloWorldServer();
            // add endpoints on all IP addresses
            server.addEndpoints();
            server.start();

        } catch (SocketException e) {
            System.err.println("Failed to initialize server: " + e.getMessage());
        }
    }

    /**
     * Add individual endpoints listening on default CoAP port on all IPv4 addresses of all network interfaces.
     */
    private void addEndpoints() {
    	for (InetAddress addr : EndpointManager.getEndpointManager().getNetworkInterfaces()) {
    		// only binds to IPv4 addresses and localhost
			if (addr instanceof Inet4Address || addr.isLoopbackAddress()) {
				InetSocketAddress bindToAddress = new InetSocketAddress(addr, COAP_PORT);
				addEndpoint(new CoapEndpoint(bindToAddress));
			}
		}
    }

    /*
     * Constructor for a new Hello-World server. Here, the resources
     * of the server are initialized.
     */
    public HelloWorldServer() throws SocketException {
		
		// create gpio controller
		gpio = GpioFactory.getInstance();

		// for getting sensor device
		w1Master = new W1Master();
		
		// provision temperature sensor
		for(TemperatureSensor device : w1Master.getDevices(TemperatureSensor.class)){
			if(device.getName().contains("28-0000075565ad")){
				tempSensor = device;
			}
		}
        
        // provide an instance of a Temperature resource
        add(new TemperatureResource());
        
        // provide an instance of a Second resource
        add(new SecondResource());
    }

    /*
     * Definition of the Temperature Resource
     */
    class TemperatureResource extends CoapResource {
        
        public TemperatureResource() {
            
            // set resource identifier
            super("temperature");
            
            // set display name
            getAttributes().setTitle("Temperature Resource");
        }

        @Override
        public void handleGET(CoapExchange exchange) {
			
			double temp = tempSensor.getTemperature(TemperatureScale.CELSIUS);
			String tempstr = String.valueOf(temp);
			
            // respond to the request
            exchange.respond(tempstr);
        }
    }
    
    /*
     * Definition of the Second Resource
     */
    class SecondResource extends CoapResource {
        
        public SecondResource() {
            
            // set resource identifier
            super("second");
            
            // set display name
            getAttributes().setTitle("Second Resource");
        }

        @Override
        public void handleGET(CoapExchange exchange) {
            
            // respond to the request
            exchange.respond("Second!");
        }
    }
}

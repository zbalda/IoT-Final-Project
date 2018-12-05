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

// GPIO libraries
import com.pi4j.io.gpio.*;
import com.pi4j.component.temperature.TemperatureSensor;
import com.pi4j.io.w1.W1Master;
import com.pi4j.temperature.TemperatureScale;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;

public class HelloWorldServer extends CoapServer {

	// GPIO variables
	public static GpioController gpio;

	// Sensor variables
	public static W1Master w1Master;
	public static TemperatureSensor tempSensor;
  public static GpioPinDigitalOutput heaterPin;
  public static GpioPinDigitalOutput fanPin;

	// state variables
	public static double goalTemp;
	public static boolean climateControlOn;
	public static boolean heaterOn;
	public static boolean fanOn;

	private static final int COAP_PORT = NetworkConfig.getStandard().getInt(NetworkConfig.Keys.COAP_PORT);
    /*
     * Application entry point.
     */
    public static void main(String[] args) {

        try {

            // create server
            HelloWorldServer server = new HelloWorldServer();

						// new thread for ClimateDevices
						Thread ClimateDevicesThread = new Thread() {
							@Override
							public void run(){
								ClimateDevices();
							}
						};
						ClimateDevicesThread.start();
						System.out.println("ClimateDevicesThread Started.");

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

		static public void ClimateDevices() {
			while (true) {
				// update devices
				double temp = tempSensor.getTemperature(TemperatureScale.CELSIUS);
				if(climateControlOn) {
					if(temp < goalTemp-.5) {
						// turn heater on
            heaterPin.high();
						// turn fan off
            fanPin.low();
					} else if (temp > goalTemp+.5) {
						// turn heater off
            heaterPin.low();
						// turn fan on
            fanPin.high();
					} else {
						// turn heater off
            heaterPin.low();
						// turn fan off
            fanPin.low();
					}
				} else {
					// turn heater off
          heaterPin.low();
					// turn fan off
          fanPin.low();
				}

				// sleep
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ex) {
					System.out.println("Tried to sleep ClimateDevices() but couldn't.");
				}
			}
		}

    /*
     * Constructor for a new Hello-World server. Here, the resources
     * of the server are initialized.
     */
    public HelloWorldServer() throws SocketException {

			// initialize state variables
			goalTemp = 20;
			climateControlOn = false;
			heaterOn = false;
			fanOn = false;

			// create gpio controller
			gpio = GpioFactory.getInstance();

			// provision output pin for heater
      heaterPin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_25, "heater", PinState.LOW);
      heaterPin.setShutdownOptions(true, PinState.LOW, PinPullResistance.OFF);
      
			// provision output pin for fan
      fanPin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_27, "fan", PinState.LOW);
      fanPin.setShutdownOptions(true, PinState.LOW, PinPullResistance.OFF);

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

      // provide an instance of a Climate Control resource
      add(new ClimateControlResource());

      // provide an instance of a Heater resource
      add(new HeaterResource());

      // provide an instance of a Fan resource
      add(new FanResource());
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

						// get temperature
						double temp = tempSensor.getTemperature(TemperatureScale.CELSIUS);
						String tempstr = String.valueOf(temp);

            // respond to the request
            exchange.respond(tempstr);
        }

        @Override
        public void handlePOST(CoapExchange exchange) {
          try {
            String payloadStr = new String(exchange.getRequestPayload());
            System.out.println("Request Payload (Text): " + payloadStr);
            int newTempTarget = Integer.parseInt(payloadStr);
            // set new temperature target
            goalTemp = newTempTarget;
            exchange.accept();
            // couldn't cast to int
          } catch (NumberFormatException e) {
            String payloadStr = new String(exchange.getRequestPayload());
            System.out.println("Couldn't convert " + payloadStr + " to int.");
            exchange.reject();
          }
        }
    }

    /*
     * Definition of the Climate Control Resource
     */
    class ClimateControlResource extends CoapResource {

        public ClimateControlResource() {

            // set resource identifier
            super("climate-control");

            // set display name
            getAttributes().setTitle("Climate Control Resource");
        }

        @Override
        public void handleGET(CoapExchange exchange) {

					// respond to the request
					if(climateControlOn)
					{
						exchange.respond("on");
					} else {
						exchange.respond("off");
					}
        }

        @Override
        public void handlePOST(CoapExchange exchange) {
          String payloadStr = new String(exchange.getRequestPayload());
          System.out.println("Request Payload (Text): " + payloadStr);
          if(payloadStr == "on") {
            climateControlOn = true;
            exchange.accept();
          } else if(payloadStr == "off") {
            climateControlOn = false;
            exchange.accept();
          } else {
            System.out.println("This is not a valid command: " + payloadStr);
            exchange.reject();
          }
        }
    }

	/*
     * Definition of the Heater Resource
     */
    class HeaterResource extends CoapResource {

        public HeaterResource() {

            // set resource identifier
            super("heater");

            // set display name
            getAttributes().setTitle("Heater Resource");
        }

        @Override
        public void handleGET(CoapExchange exchange) {

            // respond to the request
						if(heaterOn)
						{
							exchange.respond("on");
						} else {
							exchange.respond("off");
						}
        }
    }

    /*
     * Definition of the Fan Resource
     */
    class FanResource extends CoapResource {

        public FanResource() {

            // set resource identifier
            super("fan");

            // set display name
            getAttributes().setTitle("Fan Resource");
        }

        @Override
        public void handleGET(CoapExchange exchange) {

            // respond to the request
						if(fanOn)
						{
							exchange.respond("on");
						} else {
							exchange.respond("off");
						}
        }
    }
}

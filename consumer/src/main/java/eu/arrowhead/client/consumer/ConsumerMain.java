/*
 *  Copyright (c) 2018 AITIA International Inc.
 *
 *  This work is part of the Productive 4.0 innovation project, which receives grants from the
 *  European Commissions H2020 research and innovation programme, ECSEL Joint Undertaking
 *  (project no. 737459), the free state of Saxony, the German Federal Ministry of Education and
 *  national funding authorities from involved countries.
 */

package eu.arrowhead.client.consumer;

import eu.arrowhead.client.common.Utility;
import eu.arrowhead.client.common.exception.ArrowheadException;
import eu.arrowhead.client.common.misc.TypeSafeProperties;
import eu.arrowhead.client.common.model.ArrowheadService;
import eu.arrowhead.client.common.model.ArrowheadSystem;
import eu.arrowhead.client.common.model.OrchestrationResponse;
import eu.arrowhead.client.common.model.ServiceRequestForm;
import eu.arrowhead.client.common.model.TemperatureReadout;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import org.glassfish.jersey.SslConfigurator;

public class ConsumerMain {

  private static boolean isSecure;
  private static final TypeSafeProperties props = Utility.getProp("app.properties");

  public static void main(String[] args) {
    System.out.println("Working directory: " + System.getProperty("user.dir"));

    String orchAddress = props.getProperty("orch_address", "0.0.0.0");
    int orchInsecurePort = props.getIntProperty("orch_insecure_port", 8440);
    int orchSecurePort = props.getIntProperty("orch_secure_port", 8441);

    for (String arg : args) {
      if (arg.equals("-tls")) {
        isSecure = true;
        SslConfigurator sslConfig = SslConfigurator.newInstance().trustStoreFile(props.getProperty("truststore"))
                                                   .trustStorePassword(props.getProperty("truststorepass"))
                                                   .keyStoreFile(props.getProperty("keystore")).keyStorePassword(props.getProperty("keystorepass"))
                                                   .keyPassword(props.getProperty("keypass"));
        SSLContext sslContext = sslConfig.createSSLContext();
        Utility.setSSLContext(sslContext);
        break;
      }
    }

    String ORCH_URI;
    if (isSecure) {
      ORCH_URI = Utility.getUri(orchAddress, orchSecurePort, "orchestrator/orchestration", true, false);
    } else {
      ORCH_URI = Utility.getUri(orchAddress, orchInsecurePort, "orchestrator/orchestration", false, false);
    }

    long startTime = System.currentTimeMillis();

    //Payload compiling
    ServiceRequestForm srf = compileSRF();
    System.out.println("Service Request payload: " + Utility.toPrettyJson(null, srf));

    //Sending request to the orchestrator, parsing the response
    Response postResponse = Utility.sendRequest(ORCH_URI, "POST", srf);
    OrchestrationResponse orchResponse = postResponse.readEntity(OrchestrationResponse.class);
    System.out.println("Orchestration Response payload: " + Utility.toPrettyJson(null, orchResponse));

    if (orchResponse.getResponse().isEmpty()) {
      throw new ArrowheadException("Orchestrator returned with 0 Orchestration Forms!");
    }
    ArrowheadSystem provider = orchResponse.getResponse().get(0).getProvider();
    String serviceURI = orchResponse.getResponse().get(0).getServiceURI();
    UriBuilder ub = UriBuilder.fromPath("").host(provider.getAddress()).scheme("http");
    if (serviceURI != null) {
      ub.path(serviceURI);
    }
    if (provider.getPort() > 0) {
      ub.port(provider.getPort());
    }
    if (orchResponse.getResponse().get(0).getService().getServiceMetadata().containsKey("security")) {
      ub.scheme("https");
      ub.queryParam("token", orchResponse.getResponse().get(0).getAuthorizationToken());
      ub.queryParam("signature", orchResponse.getResponse().get(0).getSignature());
    }
    System.out.println("Received provider system URL: " + ub.toString());

    //Sending request to the provider, parsing the answer
    Response getResponse = Utility.sendRequest(ub.toString(), "GET", null);
    TemperatureReadout readout = new TemperatureReadout();
    try {
      readout = getResponse.readEntity(TemperatureReadout.class);
    } catch (RuntimeException e) {
      e.printStackTrace();
      System.out.println("Provider did not send the temperature readout in SenML format.");
    }
    if (readout.getE().get(0) == null) {
      System.out.println("Provider did not send any MeasurementEntry.");
    } else {
      long endTime = System.currentTimeMillis();
      System.out.println("The indoor temperature is " + readout.getE().get(0).getV() + " degrees celsius.");
      System.out.println("Orchestration and Service consumption response time: " + Long.toString(endTime - startTime));
    }
  }

  private static ServiceRequestForm compileSRF() {
    ArrowheadSystem consumer = new ArrowheadSystem("client1", "localhost", 0, "null");

    Map<String, String> metadata = new HashMap<>();
    metadata.put("unit", "celsius");
    if (isSecure) {
      metadata.put("security", "token");
    }
    ArrowheadService service = new ArrowheadService("IndoorTemperature", Collections.singletonList("json"), metadata);

    Map<String, Boolean> orchestrationFlags = new HashMap<>();
    orchestrationFlags.put("overrideStore", true);
    orchestrationFlags.put("pingProviders", false);
    orchestrationFlags.put("metadataSearch", true);
    orchestrationFlags.put("enableInterCloud", true);

    return new ServiceRequestForm.Builder(consumer).requestedService(service).orchestrationFlags(orchestrationFlags).build();
  }
}
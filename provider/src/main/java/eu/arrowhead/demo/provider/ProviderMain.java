package eu.arrowhead.demo.provider;

import eu.arrowhead.common.api.ArrowheadClient;
import eu.arrowhead.common.api.ArrowheadServer;
import eu.arrowhead.common.api.ArrowheadSecurityContext;
import eu.arrowhead.common.api.clients.ServiceRegistryClient;
import eu.arrowhead.common.model.ServiceRegistryEntry;

class ProviderMain extends ArrowheadClient {

  public static void main(String[] args) {
    new ProviderMain(args).start();
  }

  public ProviderMain(String[] args) {
    super(args);
  }

  @Override
  protected void onStart(ArrowheadSecurityContext securityContext) {
    final ArrowheadServer server = ArrowheadServer.createFromProperties(securityContext);
    server.start(
            new Class[] { TemperatureResource.class, RestResource.class },
            new String[] { "eu.arrowhead.common", "eu.arrowhead.demo" }
    );

    final ServiceRegistryClient registry = ServiceRegistryClient.createFromProperties(securityContext);
    registry.register(ServiceRegistryEntry.createFromProperties(server));
  }

  @Override
  protected void onStop() {
    
  }

}
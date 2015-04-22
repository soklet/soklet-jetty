## Soklet Jetty

#### What Is It?

[Jetty](http://eclipse.org/jetty) integration for [Soklet](http://soklet.com), a minimalist infrastructure for Java webapps and microservices.

#### License

[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)

#### Maven Installation

Coming soon
<!--
```xml
<dependency>
  <groupId>com.soklet</groupId>
  <artifactId>soklet</artifactId>
  <version>1.0.0</version>
</dependency>
```
-->
#### Direct Download

Coming soon
<!-- [https://www.soklet.com/releases/soklet-1.0.0.jar](https://www.soklet.com/releases/soklet-1.0.0.jar) -->
## Example Code

```java
// Assumes you're using Guice as your DI framework via soklet-guice
public static void main(String[] args) throws Exception {
  Injector injector = createInjector(Modules.override(new SokletModule()).with(new AppModule()));
  Server server = injector.getInstance(Server.class);
  server.start();
  System.in.read(); // Wait for keypress
  server.stop();
}

class AppModule extends AbstractModule {
  @Inject
  @Provides
  @Singleton
  public Server provideServer(InstanceProvider instanceProvider) {
    // We'll have Jetty be our Soklet server
    return new JettyServer(JettyServerConfiguration.builder(instanceProvider).port(8080).build());
  }
}
```
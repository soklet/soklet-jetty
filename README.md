## Soklet Jetty

#### What Is It?

[Jetty](http://eclipse.org/jetty) integration for [Soklet](http://soklet.com), a minimalist infrastructure for Java webapps and microservices.

#### License

[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)

#### Maven Installation

```xml
<dependency>
  <groupId>com.soklet</groupId>
  <artifactId>soklet-jetty</artifactId>
  <version>1.0.8</version>
</dependency>
```

#### Direct Download

If you don't use Maven, you can drop [soklet-jetty-1.0.8.jar](http://central.maven.org/maven2/com/soklet/soklet-jetty/1.0.8/soklet-jetty-1.0.8.jar) directly into your project.  You'll also need [Jetty 9](http://download.eclipse.org/jetty/stable-9/dist/) as a dependency.

## Example Code

```java
// Assumes you're using Guice as your DI framework via soklet-guice
public static void main(String[] args) throws Exception {
  Injector injector = createInjector(Modules.override(new SokletModule()).with(new AppModule()));
  Server server = injector.getInstance(Server.class);

  // Start the server
  new ServerLauncher(server).launch(StoppingStrategy.ON_KEYPRESS, () -> {
    // Some custom on-server-shutdown code here, if needed
  });
}

class AppModule extends AbstractModule {
  @Inject
  @Provides
  @Singleton
  public Server provideServer(InstanceProvider instanceProvider) {
    // We'll have Jetty be our Soklet server
    return JettyServer.forInstanceProvider(instanceProvider).port(8080).build();
  }
}
```

See the [Soklet website](http://soklet.com) for complete documentation of server configuration options.

## About

Soklet Jetty was created by [Mark Allen](http://revetkn.com) and sponsored by [Transmogrify, LLC.](http://xmog.com)
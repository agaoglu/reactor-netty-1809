# reactor-netty-1809

reproducible example for https://github.com/reactor/reactor-netty/issues/1809

It connects to an address and requests OPTIONS. It keeps requesting OPTIONS
after each response.

When it fails to connect to an address, it will retry with backoff.

If a connection is closed after a request is sent or a response is received, it
will retry the same after 1 second.

## Running

```bash
mvn spring-boot:run
```

Then keep refreshing http://localhost:8080/actuator/prometheus to see metrics
increasing like:

```text
reactor_netty_connection_provider_pending_connections{id="334186373",name="tcp",remote_address="google.com:80",} 0.0
reactor_netty_connection_provider_pending_connections{id="-374804107",name="tcp",remote_address="google.com:80",} 0.0
reactor_netty_connection_provider_pending_connections{id="54790318",name="tcp",remote_address="google.com:80",} 0.0
reactor_netty_connection_provider_pending_connections{id="1262709027",name="tcp",remote_address="google.com:80",} 0.0
reactor_netty_connection_provider_pending_connections{id="574920672",name="tcp",remote_address="google.com:80",} 0.0
reactor_netty_connection_provider_pending_connections{id="-109696050",name="tcp",remote_address="google.com:80",} 0.0
reactor_netty_connection_provider_pending_connections{id="-907854815",name="tcp",remote_address="google.com:80",} 0.0
reactor_netty_connection_provider_pending_connections{id="-1275701648",name="tcp",remote_address="google.com:80",} 0.0
reactor_netty_connection_provider_pending_connections{id="1021400135",name="tcp",remote_address="google.com:80",} 0.0
reactor_netty_connection_provider_pending_connections{id="210526725",name="tcp",remote_address="google.com:80",} 0.0
reactor_netty_connection_provider_pending_connections{id="1962892069",name="tcp",remote_address="google.com:80",} 0.0
reactor_netty_connection_provider_pending_connections{id="982459730",name="tcp",remote_address="google.com:80",} 0.0
```

## Fixing it

Comment out or remove wiretap line in Collector. Re-run.
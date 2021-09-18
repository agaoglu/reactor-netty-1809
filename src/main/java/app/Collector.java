package app;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.rtsp.*;
import io.netty.handler.logging.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.tcp.TcpClient;
import reactor.util.retry.Retry;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Component
@Scope("prototype")
public class Collector {

    Logger logger = LoggerFactory.getLogger(Collector.class);

    private int cseq = 1;
    private boolean reconnect = true;
    private Disposable connection;
    private Disposable task;
    
    private final String address;
    
    public Collector(String address) {
        this.address = address;
    }
    
    public void start() {
        reconnect = true;
        connect();
    }

    public void stop() {
        reconnect = false;
        if (connection != null) {
            connection.dispose();
            connection = null;
        }
        if (task != null) {
            task.dispose();
            task = null;
        }
    }


    private void connect() {
        if (!reconnect) {
            if (connection != null) {
                connection.dispose();
                connection = null;
            }
            return;
        }

        logger.info("Connecting to {}", address);
        cseq = 1;
        final URI uri = URI.create(address);

        task = TcpClient.create()
                .metrics(true)
                // Disable wiretap to fix the leak
                .wiretap("wiretap", LogLevel.TRACE)
                .host(uri.getHost())
                .port(uri.getPort() < 0 ? 554 : uri.getPort())
                .doOnDisconnected(dc -> {
                    logger.warn("Connection to {} lost", address);
                    if (reconnect) {
                        // try to reconnect after 1 second
                        Mono.delay(Duration.ofSeconds(1))
                                .doOnNext(aLong -> connect())
                                .subscribe();
                    }
                })
                .connect()
                .retryWhen(Retry // Connection failures
                        .backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(60))
                        .filter(e -> e instanceof ConnectException || e instanceof NoRouteToHostException)
                        .doBeforeRetry(retrySignal -> {
                            logger.warn("Retrying connection to {}", address);
                        }))
                .flatMapMany(c -> {
                    // Configure connection
                    connection = c
                            .addHandler(new RtspEncoder())
                            .addHandler(new RtspDecoder());

                    // Keep sending requests and receiving responses
                    final Flux<Object> eventFlux = c.inbound()
                            .receiveObject()
                            .timeout(Duration.ofSeconds(1), Schedulers.parallel())
                            .doOnError(TimeoutException.class, e -> {
                                c.dispose(); // disconnected handler will retry
                            })
                            .flatMap(o -> {
                                // Write response to output
                                logger.info("Received {}", o);
                                // Re-send OPTIONS request
                                return c.outbound().sendObject(Mono.just(optionsRequest(address)));
                            });

                    // First request
                    c.outbound().sendObject(Mono.just(optionsRequest(address))).then().subscribe();
                    return eventFlux;
                })
                .subscribe();
    }

    private HttpRequest optionsRequest(String url) {
        var r = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.OPTIONS, url);
        r.headers().add(RtspHeaderNames.CSEQ, cseq++);
        return r;
    }
}

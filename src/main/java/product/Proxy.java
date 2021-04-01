package product;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.RequesterBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpFilterChain;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;

import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.pool.ConnPoolListener;
import org.apache.hc.core5.pool.ConnPoolStats;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * Example of embedded HTTP/1.1 reverse proxy using classic I/O.
 */
public class Proxy {

    public static void main(final String[] args) throws Exception {
        
        int port = 8080;

        final HttpRequester requester = RequesterBootstrap.bootstrap()
                .setStreamListener(new Http1StreamListener() {

                    @Override
                    public void onRequestHead(final HttpConnection connection, final HttpRequest request) {
                        System.out.println("[proxy->origin] " + Thread.currentThread()  + " " +
                                request.getMethod() + " " + request.getRequestUri());
                    }

                    @Override
                    public void onResponseHead(final HttpConnection connection, final HttpResponse response) {
                        System.out.println("[proxy<-origin] " + Thread.currentThread()  + " status " + response.getCode());
                    }

                    @Override
                    public void onExchangeComplete(final HttpConnection connection, final boolean keepAlive) {
                        System.out.println("[proxy<-origin] " + Thread.currentThread() + " exchange completed; " +
                                "connection " + (keepAlive ? "kept alive" : "cannot be kept alive"));
                    }

                })
                .setConnPoolListener(new ConnPoolListener<HttpHost>() {

                    @Override
                    public void onLease(final HttpHost route, final ConnPoolStats<HttpHost> connPoolStats) {
                        final StringBuilder buf = new StringBuilder();
                        buf.append("[proxy->origin] ").append(Thread.currentThread()).append(" connection leased ").append(route);
                        System.out.println(buf);
                    }

                    @Override
                    public void onRelease(final HttpHost route, final ConnPoolStats<HttpHost> connPoolStats) {
                        final StringBuilder buf = new StringBuilder();
                        buf.append("[proxy->origin] ").append(Thread.currentThread()).append(" connection released ").append(route);
                        final PoolStats totals = connPoolStats.getTotalStats();
                        buf.append("; total kept alive: ").append(totals.getAvailable()).append("; ");
                        buf.append("total allocated: ").append(totals.getLeased() + totals.getAvailable());
                        buf.append(" of ").append(totals.getMax());
                        System.out.println(buf);
                    }

                })
                .create();

        final HttpServer server = ServerBootstrap.bootstrap()
                .setListenerPort(port)
                .setStreamListener(new Http1StreamListener() {

                    @Override
                    public void onRequestHead(final HttpConnection connection, final HttpRequest request) {
                        System.out.println("[client->proxy] " + Thread.currentThread() + " " +
                                request.getMethod() + " " + request.getRequestUri());
                    }

                    @Override
                    public void onResponseHead(final HttpConnection connection, final HttpResponse response) {
                        System.out.println("[client<-proxy] " + Thread.currentThread() + " status " + response.getCode());
                    }

                    @Override
                    public void onExchangeComplete(final HttpConnection connection, final boolean keepAlive) {
                        System.out.println("[client<-proxy] " + Thread.currentThread() + " exchange completed; " +
                                "connection " + (keepAlive ? "kept alive" : "cannot be kept alive"));
                    }

                })
                .setExceptionListener(new ExceptionListener() {

                    @Override
                    public void onError(final Exception ex) {
                        if (ex instanceof SocketException) {
                            System.out.println("[client->proxy] " + Thread.currentThread() + " " + ex.getMessage());
                        } else {
                            System.out.println("[client->proxy] " + Thread.currentThread()  + " " + ex.getMessage());
                            ex.printStackTrace(System.out);
                        }
                    }

                    @Override
                    public void onError(final HttpConnection connection, final Exception ex) {
                        if (ex instanceof SocketTimeoutException) {
                            System.out.println("[client->proxy] " + Thread.currentThread() + " time out");
                        } else if (ex instanceof SocketException || ex instanceof ConnectionClosedException) {
                            System.out.println("[client->proxy] " + Thread.currentThread() + " " + ex.getMessage());
                        } else {
                            System.out.println("[client->proxy] " + Thread.currentThread() + " " + ex.getMessage());
                            ex.printStackTrace(System.out);
                        }
                    }

                })
                .addFilterFirst("my-filter", (request, responseTrigger, context, chain) -> {
                    if (!UrltoLocal.isLocal(request.getRequestUri())) {
                        //final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK);
                        //response.setEntity(new StringEntity("Welcome", ContentType.TEXT_PLAIN));
                        //responseTrigger.submitResponse(response);
                    	HttpHost remote = null;
						try {
							String name = request.getAuthority().getHostName();
							int p = request.getAuthority().getPort();
							remote = HttpHost.create(name +":" + p);
						} catch (URISyntaxException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
                    	
                    	ProxyFilter pfilter = new ProxyFilter(remote,requester);
                    	ClassicHttpResponse response  = pfilter.handle(request);
                    	responseTrigger.submitResponse(response);
                    } else {
                        chain.proceed(request, new HttpFilterChain.ResponseTrigger() {

                            @Override
                            public void sendInformation(final ClassicHttpResponse response) throws HttpException, IOException {
                                responseTrigger.sendInformation(response);
                            }

                            @Override
                            public void submitResponse(final ClassicHttpResponse response) throws HttpException, IOException {
                                response.addHeader("X-Filter", "My-Filter");
                                responseTrigger.submitResponse(response);
                            }

                        }, context);
                    }
                })
                .register("*", (request, response, context) -> {
                    // do something useful
                	// file filter or file handler
                    response.setCode(HttpStatus.SC_OK);
                    response.setEntity(new StringEntity("Hello"));
                })
                .create();

        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close(CloseMode.GRACEFUL);
            requester.close(CloseMode.GRACEFUL);
        }));

        System.out.println("Listening on port " + port);
        server.awaitTermination(TimeValue.MAX_VALUE);
    }

  

}
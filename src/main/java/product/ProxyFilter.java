package product;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;

public class ProxyFilter {

	 private final HttpHost targetHost;
     private final HttpRequester requester;

     public ProxyFilter(
             final HttpHost targetHost,
             final HttpRequester requester) {
         super();
         this.targetHost = targetHost;
         this.requester = requester;
     }

    
     public ClassicHttpResponse handle(
             final ClassicHttpRequest incomingRequest            
             ) throws HttpException, IOException {
    	 ClassicHttpResponse outgoingResponse = new BasicClassicHttpResponse(HttpStatus.SC_OK);
         final HttpCoreContext clientContext = HttpCoreContext.create();
         final ClassicHttpRequest outgoingRequest = new BasicClassicHttpRequest(
                 incomingRequest.getMethod(),
                 targetHost,
                 incomingRequest.getPath());
         for (final Iterator<Header> it = incomingRequest.headerIterator(); it.hasNext(); ) {
             final Header header = it.next();
             if (!HOP_BY_HOP.contains(header.getName().toLowerCase(Locale.ROOT))) {
                 outgoingRequest.addHeader(header);
             }
         }
         outgoingRequest.setEntity(incomingRequest.getEntity());
         final ClassicHttpResponse incomingResponse = requester.execute(
                 targetHost, outgoingRequest, Timeout.ofMinutes(1), clientContext);
         outgoingResponse.setCode(incomingResponse.getCode());
         for (final Iterator<Header> it = incomingResponse.headerIterator(); it.hasNext(); ) {
             final Header header = it.next();
             if (!HOP_BY_HOP.contains(header.getName().toLowerCase(Locale.ROOT))) {
                 outgoingResponse.addHeader(header);
             }
         }
         outgoingResponse.setEntity(incomingResponse.getEntity());
         return outgoingResponse;
     }
     
     private final static Set<String> HOP_BY_HOP = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
             HttpHeaders.HOST.toLowerCase(Locale.ROOT),
             HttpHeaders.CONTENT_LENGTH.toLowerCase(Locale.ROOT),
             HttpHeaders.TRANSFER_ENCODING.toLowerCase(Locale.ROOT),
             HttpHeaders.CONNECTION.toLowerCase(Locale.ROOT),
             HttpHeaders.KEEP_ALIVE.toLowerCase(Locale.ROOT),
             HttpHeaders.PROXY_AUTHENTICATE.toLowerCase(Locale.ROOT),
             HttpHeaders.TE.toLowerCase(Locale.ROOT),
             HttpHeaders.TRAILER.toLowerCase(Locale.ROOT),
             HttpHeaders.UPGRADE.toLowerCase(Locale.ROOT))));
	
}

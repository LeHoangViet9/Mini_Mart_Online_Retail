package vn.minimart.controller;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
@RestController
public class GatewayController {
 private final RestClient client;
 private final Map<String,String> routes;
 GatewayController(RestClient.Builder builder,
 @Value("${CATALOG_URL:http://catalog-service:8080}") String catalog,
 @Value("${CUSTOMER_URL:http://customer-service:8080}") String customer,
 @Value("${ORDER_URL:http://order-service:8080}") String order,
 @Value("${SHIPPING_URL:http://shipping-service:8080}") String shipping) {
  routes=Map.of("products",catalog,"customers",customer,"orders",order,"shipping",shipping);
  var factory=new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
  factory.setReadTimeout(Duration.ofSeconds(40)); client=builder.requestFactory(factory).build();
 }
 @RequestMapping("/api/{service}/**")
 public ResponseEntity<byte[]> proxy(@PathVariable String service,HttpServletRequest req,@RequestBody(required=false) byte[] body) {
  String path=req.getRequestURI(), method=req.getMethod();
  boolean allowed=(method.equals("POST") && path.equals("/api/orders"))
   || (method.equals("GET") && path.matches("/api/(orders/[a-fA-F0-9-]{36}|products/[0-9]+|customers/[0-9]+)"))
   || (method.equals("PUT") && path.equals("/api/shipping/rejection"));
  if(!allowed) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  try {
   var call=client.method(HttpMethod.valueOf(method)).uri(routes.get(service)+path).header("X-Trace-Id",MDC.get("trace.id"));
   if(body!=null) call.contentType(MediaType.APPLICATION_JSON).body(body);
   return call.exchange((request,response)-> {
    var headers=new HttpHeaders();
    if(response.getHeaders().getContentType()!=null) headers.setContentType(response.getHeaders().getContentType());
    headers.set("X-Trace-Id",MDC.get("trace.id"));
    return new ResponseEntity<>(response.getBody().readAllBytes(),headers,response.getStatusCode());
   });
  } catch(ResourceAccessException e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"UPSTREAM_UNAVAILABLE"); }
 }
}

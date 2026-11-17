package vn.minimart;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
public class TraceFilter extends OncePerRequestFilter {
 protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
  String trace = req.getHeader("X-Trace-Id");
  if (trace == null || !trace.matches("[a-f0-9]{32}")) trace = UUID.randomUUID().toString().replace("-", "");
  try (var ignored = MDC.putCloseable("trace.id", trace)) {
   res.setHeader("X-Trace-Id", trace); chain.doFilter(req, res);
  }
 }
}

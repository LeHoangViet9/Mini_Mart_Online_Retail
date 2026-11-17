package vn.minimart;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestControllerAdvice
public class ApiErrors {
 @ExceptionHandler(ResponseStatusException.class)
 ResponseEntity<?> error(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("reason", String.valueOf(e.getReason()))); }
}

package com.example.kvdb.apihttp;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice
public class RestExceptionAdvice {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> onAny(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getClass().getSimpleName(),
                        "message", e.getMessage()==null? "" : e.getMessage()));
    }
}

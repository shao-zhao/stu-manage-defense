package com.example.stubackend.web;

import com.example.stubackend.model.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiAdvice {
  private static final Logger log = LoggerFactory.getLogger(ApiAdvice.class);

  @ExceptionHandler(AsyncRequestNotUsableException.class)
  void clientDisconnected(AsyncRequestNotUsableException ignored) {
    // The streaming client has gone away; writing a JSON response would fail again.
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<Result<Void>> api(ApiException e) {
    return ResponseEntity.status(e.status()).body(Result.fail(e.getMessage()));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<Result<Void>> resourceMissing(NoResourceFoundException ignored) {
    return ResponseEntity.status(404).body(Result.fail("资源不存在"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Result<Void>> other(Exception e) {
    log.error("Unhandled API failure", e);
    return ResponseEntity.internalServerError().body(Result.fail("服务器处理失败"));
  }
}

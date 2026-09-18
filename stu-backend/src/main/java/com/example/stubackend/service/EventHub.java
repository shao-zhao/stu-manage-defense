package com.example.stubackend.service;

import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class EventHub {
  private final Map<SseEmitter, Long> clients = new java.util.concurrent.ConcurrentHashMap<>();

  public SseEmitter connect(long accountId) {
    SseEmitter e = new SseEmitter(0L);
    clients.put(e, accountId);
    e.onCompletion(() -> clients.remove(e));
    e.onTimeout(() -> clients.remove(e));
    e.onError(error -> clients.remove(e));
    try {
      e.send(SseEmitter.event().name("heartbeat").data("connected"));
    } catch (IOException ignored) {
    }
    return e;
  }

  /** Closes existing streams as soon as an account's tokens are revoked. */
  public void disconnectAccount(long accountId) {
    clients.forEach(
        (emitter, ownerId) -> {
          if (Objects.equals(ownerId, accountId)) {
            clients.remove(emitter);
            emitter.complete();
          }
        });
  }

  public void send(String name, Object data) {
    for (Map.Entry<SseEmitter, Long> entry : clients.entrySet()) {
      if ("notification".equals(name)
          && data instanceof Map<?, ?> map
          && !Objects.equals(entry.getValue(), ((Number) map.get("accountId")).longValue()))
        continue;
      try {
        entry.getKey().send(SseEmitter.event().name(name).data(data));
      } catch (IOException | RuntimeException ex) {
        clients.remove(entry.getKey());
      }
    }
  }
}

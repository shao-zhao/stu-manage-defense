package com.example.stubackend;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.stubackend.config.DemoDataInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class DemoDataInitializerTest {
  @Test
  void markerMakesRestartLeaveDeletedDemoRecordsAlone() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    TransactionTemplate transaction = mock(TransactionTemplate.class);
    doAnswer(
            invocation -> {
              invocation.getArgument(0, java.util.function.Consumer.class).accept(null);
              return null;
            })
        .when(transaction)
        .executeWithoutResult(any());
    when(db.update("insert ignore into demo_seed_marker(marker) values('v2-demo')")).thenReturn(0);

    new DemoDataInitializer(db, transaction).run();

    verify(db).update("insert ignore into demo_seed_marker(marker) values('v2-demo')");
    verifyNoMoreInteractions(db);
  }
}

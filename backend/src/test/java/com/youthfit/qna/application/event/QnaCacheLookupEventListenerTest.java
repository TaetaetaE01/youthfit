package com.youthfit.qna.application.event;

import com.youthfit.qna.domain.model.LookupResultType;
import com.youthfit.qna.domain.model.QnaCacheLookupLog;
import com.youthfit.qna.domain.repository.QnaCacheLookupLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QnaCacheLookupEventListenerTest {

    @Mock
    QnaCacheLookupLogRepository repository;

    @InjectMocks
    QnaCacheLookupEventListener listener;

    @Test
    void 이벤트_수신시_엔티티_save_호출() {
        QnaCacheLookupEvent event = new QnaCacheLookupEvent(
                1L, "질문", "질문 정규화", LookupResultType.HIT, 10L,
                BigDecimal.valueOf(0.92), BigDecimal.valueOf(0.20), false,
                LocalDateTime.now()
        );

        listener.onLookup(event);

        ArgumentCaptor<QnaCacheLookupLog> captor = ArgumentCaptor.forClass(QnaCacheLookupLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPolicyId()).isEqualTo(1L);
        assertThat(captor.getValue().getResult()).isEqualTo(LookupResultType.HIT);
    }

    @Test
    void save_실패시_사용자경로에_예외_전파_안함() {
        QnaCacheLookupEvent event = new QnaCacheLookupEvent(
                1L, "q", "q", LookupResultType.MISS, null, null,
                BigDecimal.valueOf(0.20), true, LocalDateTime.now()
        );
        when(repository.save(any())).thenThrow(new RuntimeException("DB down"));

        // 예외 전파 안 됨 (try/catch 격리)
        listener.onLookup(event);

        verify(repository).save(any());
    }
}

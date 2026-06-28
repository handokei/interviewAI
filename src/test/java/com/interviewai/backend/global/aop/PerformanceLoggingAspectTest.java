package com.interviewai.backend.global.aop;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@DisplayName("PerformanceLoggingAspect 테스트")
@ExtendWith(MockitoExtension.class)
class PerformanceLoggingAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private Signature signature;

    private MeterRegistry meterRegistry;
    private PerformanceLoggingAspect aspect;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        aspect = new PerformanceLoggingAspect(meterRegistry);
    }

    @Test
    @DisplayName("기능_테스트_클라이언트_메서드_호출_시_정상_반환값이_전달된다")
    void 기능_테스트_클라이언트_메서드_호출_시_정상_반환값이_전달된다() throws Throwable {
        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");
        when(joinPoint.proceed()).thenReturn("expectedResult");

        Object result = aspect.logClientCalls(joinPoint);

        assertThat(result).isEqualTo("expectedResult");
    }

    @Test
    @DisplayName("기능_테스트_클라이언트_메서드_호출_시_Micrometer_타이머_메트릭이_기록된다")
    void 기능_테스트_클라이언트_메서드_호출_시_Micrometer_타이머_메트릭이_기록된다() throws Throwable {
        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("myMethod");
        when(joinPoint.proceed()).thenReturn(null);

        aspect.logClientCalls(joinPoint);

        Timer timer = meterRegistry.find("client.call.duration")
                .tag("class", "Object")
                .tag("method", "myMethod")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("기능_테스트_Micrometer_타이머에_class_태그와_method_태그가_올바르게_설정된다")
    void 기능_테스트_Micrometer_타이머에_class_태그와_method_태그가_올바르게_설정된다() throws Throwable {
        SampleClient target = new SampleClient();
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("doWork");
        when(joinPoint.proceed()).thenReturn(null);

        aspect.logClientCalls(joinPoint);

        Timer timer = meterRegistry.find("client.call.duration")
                .tag("class", "SampleClient")
                .tag("method", "doWork")
                .timer();
        assertThat(timer).isNotNull();
    }

    @Test
    @DisplayName("기능_테스트_메서드_실행_시간이_타이머에_0_이상으로_기록된다")
    void 기능_테스트_메서드_실행_시간이_타이머에_0_이상으로_기록된다() throws Throwable {
        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("slowMethod");
        when(joinPoint.proceed()).thenReturn("done");

        aspect.logClientCalls(joinPoint);

        Timer timer = meterRegistry.find("client.call.duration")
                .tag("class", "Object")
                .tag("method", "slowMethod")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    @DisplayName("예외_테스트_클라이언트_메서드에서_예외_발생_시_예외가_다시_던져진다")
    void 예외_테스트_클라이언트_메서드에서_예외_발생_시_예외가_다시_던져진다() throws Throwable {
        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("failingMethod");
        when(joinPoint.proceed()).thenThrow(new RuntimeException("클라이언트 호출 실패"));

        assertThatThrownBy(() -> aspect.logClientCalls(joinPoint))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("클라이언트 호출 실패");
    }

    @Test
    @DisplayName("예외_테스트_예외_발생_시에도_Micrometer_타이머_메트릭이_기록된다")
    void 예외_테스트_예외_발생_시에도_Micrometer_타이머_메트릭이_기록된다() throws Throwable {
        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("failingMethod");
        when(joinPoint.proceed()).thenThrow(new RuntimeException("오류"));

        try {
            aspect.logClientCalls(joinPoint);
        } catch (RuntimeException ignored) {
        }

        Timer timer = meterRegistry.find("client.call.duration")
                .tag("class", "Object")
                .tag("method", "failingMethod")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    static class SampleClient {}
}

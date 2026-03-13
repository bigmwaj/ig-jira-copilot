package ca.espconsulting.igjiracopilot.service;

import ca.espconsulting.igjiracopilot.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class PollingServiceTest {

    @Mock
    private AppProperties appProperties;

    private PollingService pollingService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getCopilot().setMaxPollAttempts(5);
        props.getCopilot().setPollIntervalMs(10); // fast for tests
        pollingService = new PollingService(props);
    }

    @Test
    void shouldReturnResultOnFirstAttempt() throws Exception {
        String result = pollingService.pollUntilResult("test", () -> "success");
        assertEquals("success", result);
    }

    @Test
    void shouldRetryUntilResultAvailable() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        String result = pollingService.pollUntilResult("test", () -> {
            if (attempts.incrementAndGet() < 3) return null;
            return "delayed-result";
        });
        assertEquals("delayed-result", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void shouldThrowExceptionWhenMaxAttemptsExhausted() {
        assertThrows(RuntimeException.class, () ->
                pollingService.pollUntilResult("test", () -> null));
    }

    @Test
    void shouldThrowExceptionWhenCallableAlwaysThrows() {
        assertThrows(RuntimeException.class, () ->
                pollingService.pollUntilResult("test", () -> {
                    throw new RuntimeException("API unavailable");
                }));
    }
}

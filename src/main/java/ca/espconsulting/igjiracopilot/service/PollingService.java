package ca.espconsulting.igjiracopilot.service;

import ca.espconsulting.igjiracopilot.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;

/**
 * Provides a blocking polling loop that calls a supplied task until a non-null
 * result is returned or the maximum number of attempts is exhausted.
 *
 * <p>This allows Camel routes to stay decoupled from the polling concern while
 * still benefiting from configurable retry behaviour.</p>
 */
@Service
public class PollingService {

    private static final Logger log = LoggerFactory.getLogger(PollingService.class);

    private final AppProperties appProperties;

    public PollingService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * Poll a callable until it returns a non-null, non-empty result.
     *
     * @param taskDescription a human-readable description used in log messages
     * @param callable        the operation to poll (should return null when not ready)
     * @return the result returned by the callable
     * @throws RuntimeException if max attempts are exhausted without a result
     */
    public String pollUntilResult(String taskDescription, Callable<String> callable) {
        int maxAttempts = appProperties.getCopilot().getMaxPollAttempts();
        long intervalMs = appProperties.getCopilot().getPollIntervalMs();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                log.debug("Polling '{}' – attempt {}/{}", taskDescription, attempt, maxAttempts);
                String result = callable.call();
                if (result != null && !result.isBlank()) {
                    log.info("Received result for '{}' after {} attempt(s)", taskDescription, attempt);
                    return result;
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Polling interrupted for: " + taskDescription, ie);
            } catch (Exception e) {
                log.warn("Poll attempt {}/{} failed for '{}': {}", attempt, maxAttempts,
                        taskDescription, e.getMessage());
            }

            if (attempt < maxAttempts) {
                sleep(intervalMs);
            }
        }

        throw new RuntimeException(
                "Polling exhausted after " + maxAttempts + " attempts for: " + taskDescription);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}

package com.example.ecommerce.order.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import static org.springframework.kafka.listener.DeadLetterPublishingRecoverer.HeaderNames.HeadersToAdd.EX_STACKTRACE;

@Configuration(proxyBeanMethods = false)
public class KafkaListenerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KafkaListenerConfiguration.class);

    @Bean
    CommonErrorHandler orderKafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaConsumerReliabilityProperties properties,
            MeterRegistry meterRegistry
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition())
        );
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(properties.recoveryTimeout());
        recoverer.excludeHeader(EX_STACKTRACE);

        ExponentialBackOffWithMaxRetries backOff = backOff(properties);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(
                InvalidEventException.class,
                ConflictingSagaOutcomeException.class
        );
        errorHandler.setRetryListeners(retryListener(meterRegistry));
        return errorHandler;
    }

    private ExponentialBackOffWithMaxRetries backOff(KafkaConsumerReliabilityProperties properties) {
        ExponentialBackOffWithMaxRetries backOff =
                new ExponentialBackOffWithMaxRetries(properties.maxRetries());
        backOff.setInitialInterval(properties.initialInterval().toMillis());
        backOff.setMultiplier(properties.multiplier());
        backOff.setMaxInterval(properties.maxInterval().toMillis());
        return backOff;
    }

    private RetryListener retryListener(MeterRegistry meterRegistry) {
        return new RetryListener() {
            @Override
            public void failedDelivery(ConsumerRecord<?, ?> record, Exception exception, int deliveryAttempt) {
                meterRegistry.counter("ecommerce.kafka.consumer.delivery.failures",
                        "topic", record.topic()).increment();
                log.warn("Kafka delivery failed topic={} partition={} offset={} attempt={} exception={}",
                        record.topic(), record.partition(), record.offset(), deliveryAttempt, applicationType(exception));
            }

            @Override
            public void recovered(ConsumerRecord<?, ?> record, Exception exception) {
                meterRegistry.counter("ecommerce.kafka.consumer.records.recovered",
                        "topic", record.topic()).increment();
                log.error("Kafka record moved to DLT topic={} partition={} offset={} exception={}",
                        record.topic(), record.partition(), record.offset(), applicationType(exception));
            }

            @Override
            public void recoveryFailed(
                    ConsumerRecord<?, ?> record,
                    Exception original,
                    Exception failure
            ) {
                meterRegistry.counter("ecommerce.kafka.consumer.recovery.failures",
                        "topic", record.topic()).increment();
                log.error("Kafka DLT publication failed topic={} partition={} offset={} originalException={} recoveryException={}",
                        record.topic(), record.partition(), record.offset(), applicationType(original), applicationType(failure));
            }
        };
    }

    private String applicationType(Throwable throwable) {
        Throwable candidate = throwable;
        while (candidate.getCause() != null
                && candidate.getClass().getName().startsWith("org.springframework.kafka")) {
            candidate = candidate.getCause();
        }
        return candidate.getClass().getSimpleName();
    }
}

package com.poc.processor.port.outbound;

import com.poc.processor.domain.FraudEvaluation;

public interface FraudDetectionPublisherPort {
    void publishFraudDetected(FraudEvaluation evaluation);
}

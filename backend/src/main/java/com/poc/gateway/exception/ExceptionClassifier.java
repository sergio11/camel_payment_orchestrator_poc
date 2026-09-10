package com.poc.gateway.exception;

public interface ExceptionClassifier {
    ExceptionCategory classify(Throwable throwable);
}

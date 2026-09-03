package com.featureflag.flag_service.observability;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationIds {

    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";
    public static final int MAX_LENGTH = 64;

    private static final Pattern SAFE_VALUE =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private CorrelationIds() {
    }

    public static String resolve(String candidate) {
        return isValid(candidate)
                ? candidate
                : UUID.randomUUID().toString();
    }

    public static boolean isValid(String candidate) {
        return candidate != null
                && candidate.length() <= MAX_LENGTH
                && SAFE_VALUE.matcher(candidate).matches();
    }

    public static String currentOrGenerate() {
        return resolve(MDC.get(MDC_KEY));
    }
}

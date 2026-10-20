package com.weida.bot.wechatilinkbot.model;

import java.util.List;

/**
 * Structured summary of a test run.
 *
 * @param passed whether all tests passed
 * @param totalTests total number of tests
 * @param passedCount number of passed tests
 * @param failedCount number of failed tests
 * @param failures list of failure descriptions (bounded)
 * @param durationMs test duration in milliseconds
 * @param timedOut whether the test timed out
 */
public record TestResult(
        boolean passed,
        int totalTests,
        int passedCount,
        int failedCount,
        List<String> failures,
        long durationMs,
        boolean timedOut) {
}

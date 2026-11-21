package com.filee.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class Logger {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private Logger() {
    }

    public static void info(String tag, String message) {
        log("INFO", tag, message);
    }

    public static void warn(String tag, String message) {
        log("WARN", tag, message);
    }

    public static void error(String tag, String message) {
        error(tag, message, null);
    }

    public static void error(String tag, String message, Throwable throwable) {
        log("ERROR", tag, message);
        if (throwable != null) {
            throwable.printStackTrace(System.err);
        }
    }

    private static void log(String level, String tag, String message) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        System.out.println(String.format("[%s] [%s] [%s] %s", timestamp, level, tag, message));
    }
}

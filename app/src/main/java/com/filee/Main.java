package com.filee;

/**
 * Main application entry point without extending Application.
 * Prevents launcher issues when executing JavaFX without explicit module parameters.
 */
public final class Main {

    private Main() {
        // Prevent instantiation
    }

    public static void main(String[] args) {
        App.launchApplication(args);
    }
}

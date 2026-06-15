package com.poe2runechecker;

/**
 * Точка входа для упакованного приложения. НЕ наследует Application —
 * иначе JavaFX из fat-jar падает с "JavaFX runtime components are missing".
 */
public class Launcher {
    public static void main(String[] args) {
        App.main(args);
    }
}

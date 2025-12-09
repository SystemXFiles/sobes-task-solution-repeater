package org.example.utils.repeater;

public interface RepeaterTask {
    default void await() throws InterruptedException {}
}

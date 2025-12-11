package org.example.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TimeUtils {
    public long now() {
        // В отличие от currentTimeMillis не зависит от системных часов.
        // Например, синхронизация часов произошла, время сдвигается в случае currentTimeMillis,
        // а nanoTime независим от этого.
        return System.nanoTime();
    }
}

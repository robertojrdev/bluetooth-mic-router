package com.btmicfix;

/** Elapsed-time session deadline. Reapplying a route never changes this deadline. */
final class SessionDeadline {
    private long deadline;

    static int validMinutes(int minutes) {
        switch (minutes) {
            case 0: case 1: case 5: case 10: case 15: case 30: case 60: return minutes;
            default: return 5;
        }
    }

    void start(long now, int minutes) {
        int duration = validMinutes(minutes);
        deadline = duration == 0 ? 0 : now + duration * 60_000L;
    }

    boolean expired(long now) { return deadline != 0 && now >= deadline; }
    boolean timed() { return deadline != 0; }
    long at() { return deadline; }
    long remaining(long now) { return deadline == 0 ? 0 : Math.max(0, deadline - now); }

    boolean extend(long now) {
        if (!timed() || expired(now)) return false;
        deadline += 5 * 60_000L;
        return true;
    }

    void clear() { deadline = 0; }
}

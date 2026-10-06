package com.btmicfix;

/** A timed-out recording must go idle before it can automatically start another session. */
final class AutomaticPolicy {
    private boolean active;
    private boolean blocked;
    void update(boolean recording) {
        active = recording;
        if (!recording) blocked = false;
    }
    boolean recording() { return active; }
    boolean mayStart() { return active && !blocked; }
    void suppressUntilIdle() { blocked = true; }
    void clear() { active = false; blocked = false; }
}

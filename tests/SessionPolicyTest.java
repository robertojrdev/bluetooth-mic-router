package com.btmicfix;

/** Host-side checks of deadlines and automatic restart policy; no Android mocks/dependencies. */
public final class SessionPolicyTest {
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        long start = 12_345;
        SessionDeadline timer = new SessionDeadline();
        timer.start(start, 5);
        check(!timer.expired(start + 299_999), "Five-minute session ended early");
        check(timer.expired(start + 300_000), "Deadline boundary must expire");
        check(!timer.extend(start + 300_001), "An expired session must not be resurrected");
        timer.start(start, 10);
        check(timer.extend(start + 60_000), "Live session should extend");
        check(timer.remaining(start + 60_000) == 840_000, "Extend adds to original deadline, not current time");
        check(timer.expired(start + 900_000), "Extended session must still expire");
        timer.start(start, 0);
        check(!timer.timed() && !timer.expired(start + 86_400_000), "Until-stop must not expire");
        check(!timer.extend(start + 1), "Indefinite session must stay indefinite");
        timer.start(start, Integer.MAX_VALUE);
        check(timer.at() == start + 300_000, "Invalid saved duration must fall back to five minutes");
        timer.start(start, -1);
        check(timer.at() == start + 300_000, "Negative duration must not bypass auto-stop");
        timer.clear();
        check(!timer.timed(), "Stop must clear the deadline");

        AutomaticPolicy auto = new AutomaticPolicy();
        auto.update(true);
        check(auto.mayStart(), "Recording start should allow routing");
        auto.suppressUntilIdle();
        auto.update(true);
        check(!auto.mayStart(), "Repeated active/config changes must not restart a timed-out session");
        auto.update(false);
        check(!auto.mayStart(), "Idle must not start routing");
        auto.update(true);
        check(auto.mayStart(), "A new recording after idle should allow routing again");
        auto.clear();
        check(!auto.recording() && !auto.mayStart(), "Disabling automatic mode must clear its state");
        System.out.println("Session deadline and automatic restart checks passed");
    }
}

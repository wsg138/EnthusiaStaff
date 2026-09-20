package net.enthusia.staff.paper.staff;

final class StaffToolSpectateTargetPolicy {
    private StaffToolSpectateTargetPolicy() {
    }

    static boolean eligible(boolean vanished, boolean exempt) {
        return !vanished && !exempt;
    }
}

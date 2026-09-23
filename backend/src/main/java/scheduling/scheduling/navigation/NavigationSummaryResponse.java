package scheduling.scheduling.navigation;

public record NavigationSummaryResponse(
        long pendingLeaveRequests,
        long pendingPreferences,
        long pendingSwapRequests,
        long unreadNotifications,
        long pendingSchedules) {
}

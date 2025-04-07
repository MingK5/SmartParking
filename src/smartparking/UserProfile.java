package smartparking;

public class UserProfile {
    public enum Role { REGULAR, VIP, CORPORATE }

    private final String userId;
    private final Role role;

    public UserProfile(String userId, Role role) {
        this.userId = userId;
        this.role = role;
    }

    public String getUserId() { return userId; }
    public Role getRole() { return role; }

    public int getMaxBookingsAllowed() {
        return switch (role) {
            case REGULAR -> 1;
            case VIP -> 2;
            case CORPORATE -> 5;
        };
    }

    @Override
    public String toString() {
        return role + " User [" + userId.substring(0, 6) + "]";
    }
}

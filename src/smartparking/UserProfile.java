package smartparking;

// Class to represent user profile with role and booking limit
public class UserProfile {
    // Enum to define user roles
    public enum Role { REGULAR, VIP, CORPORATE }
    private final String userId;
    private final Role role;

    // Constructor
    public UserProfile(String userId, Role role) {
        this.userId = userId;
        this.role = role;
    }

    // Method to get user ID
    public String getUserId() { return userId; }
    
    // Method to get user role
    public Role getRole() { return role; }

    // Method to determine booking limit based on role
    public int getMaxBookingsAllowed() {
        return switch (role) {
            case REGULAR -> 1;
            case VIP -> 2;
            case CORPORATE -> 5;
        };
    }

    // Override to return a readable string with role and shortened user ID
    // Example: "VIP User [a1b2c3]"
    @Override
    public String toString() {
        return role + " User [" + userId.substring(0, 6) + "]";
    }
}

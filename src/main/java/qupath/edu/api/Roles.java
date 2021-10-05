package qupath.edu.api;

import com.google.common.collect.Sets;

import java.util.Set;

public enum Roles {

    ANYONE("Anyone"),
    ADMIN("Administrative tasks"),
    MODERATOR("Moderator tasks"),

    MANAGE_USERS("Manage users"),
    MANAGE_SLIDES("Manage slides");

    private String description;

    Roles(String description) {
        this.description = description;
    }

    private final static Set<Roles> MODIFIABLE_ROLES = Set.of(MANAGE_USERS, MANAGE_SLIDES);

    /**
     * List of roles which can be added / removed by users with {@link #MANAGE_USERS}.
     * Additional roles available for users with the {@link #ADMIN} role.
     * @return Set<Roles>
     */
    public static Set<Roles> getModifiableRoles() {
        if (EduAPI.hasRole(Roles.ADMIN)) {
            return Sets.union(MODIFIABLE_ROLES, Set.of(Roles.ADMIN, Roles.MODERATOR));
        }

        return MODIFIABLE_ROLES;
    }

    public String getDescription() {
        return description;
    }
}

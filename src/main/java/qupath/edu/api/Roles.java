package qupath.edu.api;

import com.google.common.collect.Sets;

import java.util.Set;

public enum Roles {

    ANYONE("Anyone", ""),
    ADMIN("Administrator", "Do anything in any organization."),
    MODERATOR("Moderator", "Create new workspaces in user's' organization."),

    MANAGE_USERS("Manage users", "Create / edit / delete users in user's' organization."),
    MANAGE_SLIDES("Manage slides", "Upload / edit / delete slides in user's' organization.");

    private String name;
    private String description;

    Roles(String name, String description) {
        this.name = name;
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

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}

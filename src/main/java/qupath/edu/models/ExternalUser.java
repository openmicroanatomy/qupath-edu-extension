package qupath.edu.models;

import qupath.edu.api.Roles;

import java.util.List;

public class ExternalUser {

    private String id;
    private String name;
    private String email;
    private ExternalOrganization organization;
    private List<Roles> roles;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public ExternalOrganization getOrganization() {
        return organization;
    }

    public String getOrganizationName() {
        if (organization == null) {
            return "No organization assigned!";
        }

        return organization.getName();
    }

    /**
     * The organizationId can be null in the rare scenario, when using an
     * administrator account doesn't have a has an organization assigned yet.
     */
    public String getOrganizationId() {
        if (organization == null) {
            return null;
        }

        return organization.getId();
    }

    public List<Roles> getRoles() {
        return roles;
    }

    @Override
    public String toString() {
        return "ExternalUser{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", organization=" + organization +
                ", roles=" + roles +
                '}';
    }
}

package qupath.edu.models;

import java.util.Objects;

public class ExternalOrganization {

	private String id;
	private String name;
	private String logoUrl;

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getLogoUrl() {
		return logoUrl;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ExternalOrganization that = (ExternalOrganization) o;
		return Objects.equals(id, that.id) && Objects.equals(name, that.name) && Objects.equals(logoUrl, that.logoUrl);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, name, logoUrl);
	}

	@Override
	public String toString() {
		return name;
	}
}

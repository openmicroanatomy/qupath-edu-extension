package qupath.edu.models;

public class ExternalOrganization extends ExternalOwner {

	private String logoUrl;

	public String getLogoUrl() {
		return logoUrl;
	}

	@Override
	public String toString() {
		return "ExternalOrganization{" +
				"logoUrl='" + logoUrl + '\'' +
				", id='" + id + '\'' +
				", name='" + name + '\'' +
				'}';
	}
}

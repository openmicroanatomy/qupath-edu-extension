package qupath.edu.models;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class ExternalWorkspace {

    private String name;
    private String id;
    private ExternalOwner owner;
    private List<ExternalSubject> subjects;

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public String getOwnerId() {
        return owner.getId();
    }

    public List<ExternalSubject> getSubjects() {
        return subjects;
    }

    public Optional<ExternalSubject> findSubject(String id) {
        return subjects.stream()
                .filter(subject -> subject.getId().equalsIgnoreCase(id))
                .findFirst();
    }

    public List<ExternalProject> getAllProjects() {
        return getSubjects().stream()
                .flatMap(s -> s.getProjects().stream())
                .collect(Collectors.toList());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExternalWorkspace that = (ExternalWorkspace) o;
        return Objects.equals(name, that.name) && Objects.equals(id, that.id) && Objects.equals(owner, that.owner) && Objects.equals(subjects, that.subjects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, id, owner, subjects);
    }
}

package qupath.edu.gui.dialogs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.EduExtension;
import qupath.edu.gui.CustomDialogs;
import qupath.edu.EduProject;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;

import java.io.IOException;
import java.util.Optional;

public class ProjectDescriptionEditorCommand {

    private static final QuPathGUI qupath = QuPathGUI.getInstance();
    private static final Logger logger = LoggerFactory.getLogger(ProjectDescriptionEditorCommand.class);

    public static void openDescriptionEditor() {
        if (!(qupath.getProject() instanceof EduProject project)) {
            Dialogs.showErrorNotification(
                "Error",
                "The current project is not an EduProject -- cannot edit information."
            );

            return;
        }

        String initialInput = project.getProjectInformation();
        Optional<String> result = CustomDialogs.showWYSIWYGEditor(initialInput);

        if (result.isPresent()) {
            project.setProjectInformation(result.get());
            EduExtension.setProjectInformation(result.get());

            try {
                project.syncChanges();
            } catch (IOException e) {
                logger.error("Error while syncing changes.");
            }
        }
    }
}
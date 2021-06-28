# Building

To install the QuPath dependencies, you first need to setup a **personal access token** in Github 

1. Go to [https://github.com/settings/tokens](https://github.com/settings/tokens)
2. Create a new token with the `read:packages` scope
3. Create the following environment variables: `GITHUB_USERNAME` and `GITHUB_TOKEN`, equaling to your Github username and the token you just created.

Now you're ready to build the extension with `./gradlew build`

Find the built extension at `build/libs/qupath-edu-extension-[version].jar`

# Setting up your development environment

If you wish to run QuPath with the latest version of the extension and/or use any debugging tools provided by your IDE, then follow these instructions.

1. Download the JavaFX SDK from here [https://gluonhq.com/products/javafx/](https://gluonhq.com/products/javafx/)
2. Add the following to your **Run/Debug Configuration**:

**Main class:** `qupath.lib.gui.QuPathApp`

**VM Options:**

```
--module-path [JavaFX SDK]\lib
--add-modules=javafx.base,javafx.controls,javafx.graphics,javafx.media,javafx.web,javafx.swing
--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED
--add-exports=javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED
--add-exports=javafx.graphics/com.sun.javafx.scene.traversal=ALL-UNNAMED
--add-exports=javafx.web/com.sun.javafx.webkit=ALL-UNNAMED
```

_NB: Remember to change the `[JavaFX SDK]` to the SDK installation path_

# Building

Build the extension with `./gradlew build`

Find the built extension at `build/libs/qupath-edu-extension-[version].jar`

# Setting up your development environment

If you wish to run the development version of the extension and/or use any debugging tools provided by your IDE, then follow these instructions. 

**These instructions are written for IntelliJ and may differ slightly for other editors.**

1. Download the JavaFX 17.0.2 SDK from here [https://gluonhq.com/products/javafx/](https://gluonhq.com/products/javafx/) and extract it
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
--add-exports=javafx.controls/com.sun.javafx.scene.control.skin=ALL-UNNAMED
--add-exports=javafx.graphics/com.sun.javafx.css=ALL-UNNAMED
--add-opens=javafx.base/javafx.beans.property=ALL-UNNAMED
--add-opens=javafx.base/com.sun.javafx.binding=ALL-UNNAMED
```

**_NB: Change the `[JavaFX SDK]` to the path where you extracted the JavaFX SDK_**

![IntelliJ Run/Debug Configuration example](http://static.yli-hallila.fi/qupathedudocs/IDEA%20Configuration.png)
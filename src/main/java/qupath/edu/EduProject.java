package qupath.edu;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import javafx.scene.control.ButtonType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.api.EduAPI;
import qupath.edu.exceptions.HttpException;
import qupath.edu.gui.dialogs.WorkspaceManager;
import qupath.edu.server.EduServerBuilder;
import qupath.edu.tours.SlideTourEntry;
import qupath.edu.util.PathAnnotationObjectWithMetadata;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.ProjectCommands;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.io.GsonTools;
import qupath.lib.io.PathIO;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.ResourceManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Data structure to store multiple images and their respective data.
 * Stores everything mainly in-memory and syncs it with an external server.
 * <p>
 * Based on {@link qupath.lib.projects.DefaultProject}
 */
public class EduProject implements Project<BufferedImage> {

	private final transient String IMAGE_ID = "PROJECT_ENTRY_ID";

	private final transient Logger logger = LoggerFactory.getLogger(getClass());

	private final transient ExecutorService thumbnailLoader = Executors.newSingleThreadExecutor();

	private List<EduProjectImageEntry> images = new ArrayList<>();

	private String version;
	private String name;
	private String id;
	private String projectInformation;

	private transient boolean maskNames = false;
	private LinkedHashMap<String, String> metadata = new LinkedHashMap<>();

	private long creationTimestamp;
	private long modificationTimestamp;

	public EduProject(String projectData) throws IOException {
		// Ensure that the static method inside ImageServes is run and GSON TypeAdapters are registered.
		var servers = new ImageServers();

		Gson gson = GsonTools.getInstance();
		JsonObject element = gson.fromJson(projectData, JsonObject.class);

		id = element.get("id").getAsString();
		creationTimestamp = element.get("createTimestamp").getAsLong();
		modificationTimestamp = element.get("modifyTimestamp").getAsLong();

		if (element.has("projectInformation")) {
			projectInformation = element.get("projectInformation").getAsString();
		}

		if (element.has("version")) {
			version = element.get("version").getAsString();
		} else {
			throw new IOException("Older projects are not supported in this version of QuPath, sorry!");
		}

		if (element.has("images")) {
			EduProjectImageEntry[] images = gson.fromJson(element.get("images"), EduProjectImageEntry[].class);
			addImages(images);
		}

		if (element.has("metadata") && !element.get("metadata").getAsString().isEmpty()) {
			var json = new String(Base64.getDecoder().decode(element.get("metadata").getAsString()), StandardCharsets.UTF_8);
			var type = TypeToken.getParameterized(LinkedHashMap.class, String.class, String.class).getType();

			metadata = gson.fromJson(json, type);
		}
	}

	@Override
	public List<PathClass> getPathClasses() {
		return Collections.emptyList();
	}

	@Override
	public boolean getMaskImageNames() {
		return maskNames;
	}

	@Override
	public void setMaskImageNames(boolean maskNames) {
		this.maskNames = maskNames;
	}

	@Override
	public boolean setPathClasses(Collection<? extends PathClass> pathClasses) {
		return true;
	}

	@Override
	public URI getURI() {
		return null;
	}

	@Override
	public URI getPreviousURI() {
		return null;
	}

	@Override
	public String getVersion() {
		return version;
	}

	@Override
	public Path getPath() {
		return null;
	}

	@Override
	public Project<BufferedImage> createSubProject(String name, Collection<ProjectImageEntry<BufferedImage>> projectImageEntries) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isEmpty() {
		return images.isEmpty();
	}

	@Override
	public ProjectImageEntry<BufferedImage> addImage(ImageServerBuilder.ServerBuilder<BufferedImage> builder) {
		var entry = new EduProjectImageEntry(builder, null, null, null, null);

		images.add(entry);

		return entry;
	}

	public void addImages(EduProjectImageEntry... entries) {
		for (EduProjectImageEntry entry : entries) {
			addImage(new EduProjectImageEntry(entry));
		}
	}

	private boolean addImage(ProjectImageEntry<BufferedImage> entry) {
		if (entry instanceof EduProjectImageEntry) {
			images.add((EduProjectImageEntry) entry);
			return true;
		}

		try {
			return addImage(new EduProjectImageEntry(entry.getServerBuilder(), null, entry.getImageName(), entry.getDescription(), entry.getMetadataMap()));
		} catch (Exception e) {
			logger.error("Unable to add entry " + entry, e);
		}

		return false;
	}

	@Override
	public ProjectImageEntry<BufferedImage> addDuplicate(ProjectImageEntry<BufferedImage> entry, boolean copyData) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public ProjectImageEntry<BufferedImage> getEntry(ImageData<BufferedImage> imageData) {
		String id = (String) imageData.getProperty(IMAGE_ID);

		for (var entry : images) {
			if (entry.getID().equals(id)) {
				return entry;
			}
		}

		return null;
	}

	/**
	 * This implementation does not store any data separately, so
	 * removing an image will remove all of its data also.
	 *
	 * @param entry ProjectImageEntry to remove
	 * @param removeAllData ignored: all data always removed
	 */
	@Override
	public void removeImage(ProjectImageEntry<?> entry, boolean removeAllData) {
		// TODO: Is this an irrelevant check?
		if (entry instanceof EduProjectImageEntry) {
			images.remove(entry);
		} else {
			logger.error("Cannot remove image, is not instance of RemoteProjectImageEntry. [{}]", entry.toString());
		}
	}

	@Override
	public void removeAllImages(Collection<ProjectImageEntry<BufferedImage>> projectImageEntries, boolean removeAllData) {
		for (var entry : projectImageEntries) {
			removeImage(entry, removeAllData);
		}
	}

	@Override
	public void syncChanges() throws IOException {
		if (QuPathGUI.getInstance().isReadOnly()) {
			return;
		}

		// Check if we're previewing a backup -- skip saving changes as backups are restored manually via the
		// Backup Manager interface. Without this check any changes are persisted to the original project.
		if (name.startsWith("Backup of")) {
			return;
		}

		Gson gson = GsonTools.getInstance(true);

		JsonObject builder = new JsonObject();
		builder.addProperty("id", id);
		builder.addProperty("version", "1.0");
		builder.addProperty("createTimestamp", getCreationTimestamp());
		builder.addProperty("modifyTimestamp", System.currentTimeMillis());

		if (metadata != null) {
			builder.addProperty("metadata", Base64.getEncoder().encodeToString(gson.toJson(metadata).getBytes(StandardCharsets.UTF_8)));
		}

		if (projectInformation != null) {
			builder.addProperty("projectInformation", projectInformation);
		}

		builder.add("images", gson.toJsonTree(images));

		syncChangesToServer(gson.toJson(builder));
	}

	/**
	 * False if a guest user has denied any future login prompts for editing slides without permissions.
	 */
	private boolean promptForLogin = true;

	private void syncChangesToServer(String projectData) {
		var hasWriteAccess = false;
		var makeCopy = false;

		try {
			hasWriteAccess = EduAPI.hasWritePermission(getId());
		} catch (HttpException e) {
			logger.error("Error while syncing project.", e);

			// TODO: Store a local copy of changes and sync again when connection works again?

			Dialogs.showErrorMessage(
				"Sync error",
				"Error while syncing changes to server. If you exit now your changes will be lost; please retry later."
			);
		}

		if (!hasWriteAccess && EduAPI.getAuthType().shouldPrompt()) {
			var confirm = Dialogs.showYesNoDialog("Sync changes",
				"These changes are only visible to you because you're not authorized to edit this project. These changes will be lost after closing the project." +
				"\n\n" +
				"Do you want to make a personal copy of this project which you can edit?"
			);

			if (confirm) {
				makeCopy = true;
			} else {
				return;
			}
		} else if (!hasWriteAccess && promptForLogin) {
			var choice = Dialogs.builder()
					.title("Sync changes")
					.contentText("These changes are only visible to you because you're not logged in. These changes will be lost after closing the project" +
								 "\n\n" +
								 "Do you wish to login and sync these changes to everyone?")
					.buttons("Yes", "No", "No, don't ask me again") // TODO: Fix the order of these
					.build()
					.showAndWait()
					.orElse(new ButtonType("No"))
					.getText();

			if (choice.equals("No, don't ask me again")) {
				promptForLogin = false;
			}

			if (choice.equals("Yes")) {
				EduAPI.logout();
				EduExtension.showWorkspaceOrLoginDialog();
			}

			return;
		}

		if (makeCopy) {
			Optional<String> projectId = EduAPI.createPersonalProject(getName());

			if (projectId.isPresent()) {
				EduAPI.uploadProject(projectId.get(), projectData);

				// TODO: This prompts twice to create a personal copy because first QuPathGUI calls syncChanges() and it is ran again when opening the new project
				// 		 -- QuPathGUI#setReadOnly(true); before loading project and then to false after loading?
				WorkspaceManager.loadProject(projectId.get(), "Copy of " + getName());
			} else {
				Dialogs.showErrorNotification("Error", "Error while creating personal project. See log for possible details.");
			}
		} else if (hasWriteAccess) {
			logger.debug("Uploading project to server");

			EduAPI.uploadProject(id, projectData);

			logger.info("Changes synced to server.");
		}
	}

	@Override
	public List<ProjectImageEntry<BufferedImage>> getImageList() {
		List<ProjectImageEntry<BufferedImage>> list = new ArrayList<>(images);
		list.sort(Comparator.comparing(ProjectImageEntry::getImageName, String.CASE_INSENSITIVE_ORDER));

		return list;
	}

	@Override
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getProjectInformation() {
		return projectInformation;
	}

	public void setProjectInformation(String projectInformation) {
		this.projectInformation = projectInformation;
	}

	@Override
	public long getCreationTimestamp() {
		return creationTimestamp;
	}

	@Override
	public long getModificationTimestamp() {
		return modificationTimestamp;
	}

	@Override
	public ResourceManager.Manager<String> getScripts() {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResourceManager.Manager<ObjectClassifier<BufferedImage>> getObjectClassifiers() {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResourceManager.Manager<PixelClassifier> getPixelClassifiers() {
		throw new UnsupportedOperationException();
	}

	public Object storeMetadataValue(String key, String value) {
		if (metadata == null) {
			metadata = new LinkedHashMap<>();
		}

		return metadata.put(key, value);
	}

	public Object retrieveMetadataValue(String key) {
		return metadata == null ? null : metadata.get(key);
	}

	@Override
	public String toString() {
		return "EduProject: " + name;
	}

	@Override
	public Map<String, String> getMetadata() {
		return metadata;
	}

	public class EduProjectImageEntry implements ProjectImageEntry<BufferedImage> {

		/**
		 * ServerBuilder. This should be lightweight & capable of being JSON-ified.
		 */
		private ImageServerBuilder.ServerBuilder<BufferedImage> serverBuilder;

		/**
		 * Unique name that will be used to identify associated data files.
		 */
		private Long entryID;

		/**
		 * Randomized name that will be used when masking image names.
		 */
		private String randomizedName = UUID.randomUUID().toString();

		/**
		 * Image name to display.
		 */
		private String imageName;

		/**
		 * Image description to display.
		 */
		private String description;

		/**
		 * Map of associated metadata for the entry.
		 */
		private Map<String, String> metadata = new LinkedHashMap<>();
		private final Set<String> tags = Collections.synchronizedSet(new LinkedHashSet<>());

		/**
		 * ImageData as a base64 encoded string.
		 */
		private String imageData;

		/**
		 * SlideTour as Base64 encoded JSON.
		 */
		private byte[] slideTour;

		/**
		 * Thumbnail for this slide.
		 */
		private transient BufferedImage thumbnail;

		/**
		 * True if currently trying to fetch the thumbnail async.
		 */
		private transient ThumbnailState thumbnailState = ThumbnailState.NOT_INITIALIZED;

		/**
		 * JSON Representation of annotations. <b>Temporary until ImageData is fully JSON serializable!</b>
		 */
		public String annotations;

		EduProjectImageEntry(ImageServerBuilder.ServerBuilder<BufferedImage> builder, Long entryID, String imageName, String description, Map<String, String> metadataMap) {
			this.serverBuilder = builder;

			if (entryID == null) {
				this.entryID = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
			} else {
				this.entryID = entryID;
			}

			if (imageName == null) {
				this.imageName = "Image " + entryID;
			} else {
				this.imageName = imageName;
			}

			if (description != null) {
				setDescription(description);
			}

			if (metadataMap != null) {
				metadata.putAll(metadataMap);
			}

			try {
				readAnnotations(readImageData());
			} catch (Exception ignored) {}
		}

		public EduProjectImageEntry(EduProjectImageEntry entry) {
			this.serverBuilder = entry.serverBuilder;
			this.entryID = entry.entryID;
			this.imageName = entry.imageName;
			this.description = entry.description;
			this.metadata = entry.metadata;
			this.imageData = entry.imageData;
			this.thumbnail = entry.thumbnail;
			this.slideTour = entry.slideTour;
			this.annotations = entry.annotations;

			if (entry.tags != null) {
            	this.tags.addAll(entry.tags);
			}
        }

		@Override
		public String getID() {
			return Long.toString(entryID);
		}

		@Override
		public void setImageName(String name) {
			this.imageName = name;
			QuPathGUI.getInstance().refreshProject();
		}

		@Override
		public String getImageName() {
			if (maskNames) {
				return randomizedName;
			}

			return imageName;
		}

		@Override
		public String getOriginalImageName() {
			return imageName;
		}

		@Override
		public Path getEntryPath() {
			return null;
		}

		@Override
		public String removeMetadataValue(final String key) {
			return metadata.remove(key);
		}

		@Override
		public String getMetadataValue(final String key) {
			return metadata.get(key);
		}

		@Override
		public String putMetadataValue(final String key, final String value) {
			return metadata.put(key, value);
		}

		@Override
		public boolean containsMetadata(final String key) {
			return metadata.containsKey(key);
		}

		@Override
		public String getDescription() {
			return description;
		}

		@Override
		public void setDescription(String description) {
			this.description = description;
		}

		@Override
		public void clearMetadata() {
			this.metadata.clear();
		}

		@Override
		public Map<String, String> getMetadataMap() {
			return Collections.unmodifiableMap(metadata);
		}

		@Override
		public Collection<String> getMetadataKeys() {
			return Collections.unmodifiableSet(metadata.keySet());
		}

		@Override
		public ImageServerBuilder.ServerBuilder<BufferedImage> getServerBuilder() {
			return serverBuilder;
		}

		@Override
		public ImageData<BufferedImage> readImageData() throws IOException {
			if (imageData == null) {
				return initializeImageData();
			}

			return readImageDataBase64();
		}

		/**
		 * ImageData is stored as a Base64 encoded string inside the project properties.
		 */
		private ImageData<BufferedImage> readImageDataBase64() throws IOException {
			try (ByteArrayInputStream is = new ByteArrayInputStream(Base64.getDecoder().decode(imageData))) {
				ImageData<BufferedImage> imageData = PathIO.readImageData(is, null, serverBuilder.build(), BufferedImage.class);

				// The IMAGE_ID property is missing in some circumstances which causes slides to duplicate when opening
				if (!imageData.getProperties().containsKey(IMAGE_ID)) {
					imageData.setProperty(IMAGE_ID, entryID.toString());
				}

				imageData.setChanged(false);

				return imageData;
			} catch (Exception e) {
				throw new IOException(e);
			}
		}

		private ImageData<BufferedImage> initializeImageData() throws IOException {
			// TODO: What if serverBuilder is null? Can it be null?

			try {
				ImageData<BufferedImage> imageData = new ImageData<>(serverBuilder.build(), null, null);
				imageData.setProperty(IMAGE_ID, entryID.toString());
				imageData.setChanged(false);

				saveImageData(imageData);

				return imageData;
			} catch (Exception e) {
				throw new IOException(e);
			}
		}

		@Override
		public void saveImageData(ImageData<BufferedImage> imageData) {
			if (QuPathGUI.getInstance().isReadOnly()) {
				return;
			}

			imageData.getHistoryWorkflow().clear();
			imageData.setChanged(false);

			try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
				PathIO.writeImageData(os, imageData);

				readAnnotations(imageData);
				this.imageData = Base64.getEncoder().encodeToString(os.toByteArray());

				syncChanges();
			} catch (IOException e) {
				Dialogs.showErrorNotification("Error while saving image data", e);
			}
		}

		private void readAnnotations(ImageData<BufferedImage> imageData) {
			// TODO: Temporary fix until resolved in upstream
			Collection<PathAnnotationObjectWithMetadata> annotations = imageData.getHierarchy().getAnnotationObjects()
					.stream()
					.map(annotation -> new PathAnnotationObjectWithMetadata((PathAnnotationObject) annotation))
					.collect(Collectors.toList());

			this.annotations = GsonTools.getInstance().toJson(annotations);
		}

		public boolean hasSlideTour() {
			return slideTour != null && slideTour.length > 0;
		}

		public List<SlideTourEntry> getSlideTour() {
			if (hasSlideTour()) {
				// TODO: Remove legacy support for older projects where slide tours were stored as Base64 encoded strings causing double UTF-8 encoding
				try {
					return List.of(GsonTools.getInstance().fromJson(new String(slideTour, StandardCharsets.UTF_8), SlideTourEntry[].class));
				} catch (JsonSyntaxException e) {
					return List.of(GsonTools.getInstance().fromJson(new String(Base64.getDecoder().decode(slideTour), StandardCharsets.UTF_8), SlideTourEntry[].class));
				}
			}

			return List.of();
		}

		public void setSlideTour(List<SlideTourEntry> entries) {
			this.slideTour = GsonTools.getInstance().toJson(entries).getBytes(StandardCharsets.UTF_8);
		}

		@Override
		public PathObjectHierarchy readHierarchy() throws IOException {
			return new PathObjectHierarchy();
		}

		@Override
		public boolean hasImageData() {
			return imageData != null;
		}

		@Override
		public String getSummary() {
			StringBuilder sb = new StringBuilder();

			sb.append(getImageName()).append("\n");
			sb.append("ID:\t").append(getID()).append("\n\n");

			if (!getMetadataMap().isEmpty()) {
				for (Map.Entry<String, String> mapEntry : getMetadataMap().entrySet()) {
					sb.append(mapEntry.getKey()).append(":\t").append(mapEntry.getValue()).append("\n");
				}

				sb.append("\n");
			}

			return sb.toString();
		}

		/**
		 * Tries to download the thumbnail from the QuPath Edu Server, fallbacks to trying to generate one client-side.
		 */
		@Override public BufferedImage getThumbnail() {
			if (thumbnailState == ThumbnailState.LOADED) {
				return thumbnail;
			}

			if (thumbnailState == ThumbnailState.FAILURE) {
				return null;
			}

			if (thumbnailState == ThumbnailState.NOT_INITIALIZED) {
				thumbnailState = ThumbnailState.LOADING;

				thumbnailLoader.submit(() -> {
					// First try to fetch the thumbnail from the server, then try to generate it.
					if (fetchThumbnailFromServer() || generateThumbnail()) {
						thumbnailState = ThumbnailState.LOADED;

						QuPathGUI.getInstance().refreshProject();
					} else {
						thumbnailState = ThumbnailState.FAILURE;

						// We don't need to refresh the project here as the thumbnail is already null.
					}
				});
			}

			return null;
		}

		@Override
		public void setThumbnail(BufferedImage thumbnail) {
			this.thumbnail = thumbnail;
		}

		/**
		 * Tries to download the thumbnail from the QuPath Edu Server.
		 *
		 * @return true when fetching the thumbnail was a success
		 */
		private boolean fetchThumbnailFromServer() {
			if (!(serverBuilder instanceof EduServerBuilder)) {
				return false;
			}

			String property = "openslide.thumbnail.uri";

			try {
				Optional<JsonObject> properties = EduAPI.getSlideProperties(serverBuilder.getURIs().iterator().next());

				if (properties.isPresent() && properties.get().has(property)) {
					String thumbnailUrl = properties.get().get(property).getAsString();

					setThumbnail(ImageIO.read(new URL(thumbnailUrl)));

					return true;
				}
			} catch (Exception e) {
				logger.error("Unable to download thumbnail for {}", entryID, e);
			}

			return false;
		}

		/**
		 * Tries to generate the thumbnail client-side.
		 *
		 * @return true when generating the thumbnail was a success.
		 */
		private boolean generateThumbnail() {
			setThumbnail(null);

			try {
				setThumbnail(ProjectCommands.getThumbnailRGB(serverBuilder.build()));

				return true;
			} catch (Exception e) {
				logger.error("Unable to generate thumbnail for {}", entryID, e);
			}

			return false;
		}

		@Override
		public Collection<URI> getURIs() throws IOException {
			if (serverBuilder == null)
				return Collections.emptyList();
			return serverBuilder.getURIs();
		}

		@Override
		public boolean updateURIs(Map<URI, URI> replacements) throws IOException {
			var builderBefore = serverBuilder;
			serverBuilder = serverBuilder.updateURIs(replacements);

			return builderBefore != serverBuilder;
		}

		@Override
		public ResourceManager.Manager<ImageServer<BufferedImage>> getImages() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Set<String> getTags() {
			return Set.of();
		}

		/**
		 * <b>Temporary until ImageData is fully JSON serializable</b>
		 */
		public String getAnnotations() {
			return annotations;
		}

		@Override
		public Map<String, String> getMetadata() {
			return metadata;
		}
	}

	private enum ThumbnailState {
		NOT_INITIALIZED,
		LOADING,
		LOADED,
		FAILURE
	}
}

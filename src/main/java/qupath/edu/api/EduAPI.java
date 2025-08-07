package qupath.edu.api;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.exceptions.HttpException;
import qupath.edu.models.*;
import qupath.edu.util.VersionAdapter;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.io.GsonTools;
import qupath.lib.projects.Project;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/*
 	TODO:
 	 	- Better error management
 	 	- Authentication is a mess: rework how credentials are provided

 */
public class EduAPI {

	private final static Logger logger = LoggerFactory.getLogger(EduAPI.class);

	private static URI host;

	/**
	 * Master server provides new versions, list of public servers and so on.
	 */
	private static final URI MASTER_SERVER = URI.create("https://edu.qupath.yli-hallila.fi/");

	/**
	 * False when {@link EduAPI#authType} is equals to {@link EduAPI.AuthType#UNAUTHENTICATED}, otherwise false.
	 */
	private static final SimpleBooleanProperty connectedToServer = new SimpleBooleanProperty(false);

	public static boolean isConnectedToServer() {
		return connectedToServer.get();
	}

	public static SimpleBooleanProperty connectedToServerProperty() {
		return connectedToServer;
	}

	private static AuthType authType = AuthType.UNAUTHENTICATED;
	private static String token;
	private static String username;
	private static String password;

	private static ExternalUser user;

	/**
	 * A GUID provided by the Azure Active Directory, which is unique and consistent for every user.
	 * @see <a href="https://docs.microsoft.com/fi-fi/onedrive/find-your-office-365-tenant-id">Microsoft documentation</a>
	 */
	private static String userId;

	public static String getUserId() {
		return userId;
	}

	public static void setUserId(String userId) {
		EduAPI.userId = userId;
	}

	/**
	 * The UUID of the organization this user belongs to; null if the user is unauthenticated (i.e. a guest).
	 */
	private static String userOrganizationId;

	/**
	 * See {@link #userOrganizationId} for details.
	 * @return Users Organization UUID or null if unauthenticated.
	 */
	public static String getUserOrganizationId() {
		return userOrganizationId;
	}

	public static void setUserOrganizationId(String userOrganizationId) {
		EduAPI.userOrganizationId = userOrganizationId;
	}

	/**
	 * The UUID of the organization the user is viewing.
	 */
	private static String organizationId;

	/**
	 * See {@link #organizationId} for details.
	 * @return UUID of Organization currently viewed.
	 */
	public static String getOrganizationId() {
		return organizationId;
	}

	public static void setOrganizationId(String organizationId) {
		EduAPI.organizationId = organizationId;
	}

	/**
	 * Returns the users current organization.
	 *
	 * @return Users organization or empty
	 */
	public static Optional<ExternalOrganization> getOrganization() {
		Optional<List<ExternalOrganization>> organizations = getAllOrganizations();

		if (organizations.isEmpty()) {
			return Optional.empty();
		}

		return organizations.get().stream()
				.filter(organization -> organization.getId().equals(getUserOrganizationId()))
				.findFirst();
	}

	public static void setHost(String host) {
		if (host == null) {
			EduAPI.host = null;
		} else {
			try {
				EduAPI.host = URI.create(host);
			} catch (IllegalArgumentException ignored) {}
		}
	}

	public static URI getHost() {
		return host;
	}

	public static AuthType getAuthType() {
		return authType;
	}

	public static void setAuthType(AuthType authType) {
		connectedToServer.set(authType != AuthType.UNAUTHENTICATED);
		EduAPI.authType = authType;
	}

	public static boolean isAuthenticated() {
		return authType != AuthType.UNAUTHENTICATED;
	}

	public static boolean isGuest() {
		return authType == AuthType.GUEST;
	}

	public static void setCredentials(String username, String password) {
		if (username == null || password == null) {
			setAuthType(AuthType.UNAUTHENTICATED);
		} else {
			setAuthType(AuthType.USERNAME);
		}

		EduAPI.username = username;
		EduAPI.password = password;
	}

	public static String getToken() {
		return token;
	}

	public static String getBasicAuthHeader() {
		return basicAuth(username, password);
	}

	public static void setToken(String token) {
		if (token == null) {
			setAuthType(AuthType.UNAUTHENTICATED);
		} else {
			setAuthType(AuthType.TOKEN);
		}

		EduAPI.token = token;
	}

	private static void setUser(ExternalUser user) {
		setUserId(user.getId());
		setUserOrganizationId(user.getOrganizationId());

		if (user.getOrganizationId() == null) {
			Dialogs.showWarningNotification(
				"Missing organization",
				"Please assign an organization to your account ASAP! An account without an organization assigned may not work as intended."
			);
		}

		EduAPI.roles.setAll(user.getRoles());
		EduAPI.user = user;
	}

	private static ExternalUser getUser() {
		return EduAPI.user;
	}

	/* Roles and Permissions*/

	private static ObservableList<Roles> roles = FXCollections.observableArrayList();

	public static ObservableList<Roles> getRoles() {
		return roles;
	}

	public static boolean hasRole(Roles role) {
		// Check if we're on a local project, where everyone has all roles.
		Project<BufferedImage> project = QuPathGUI.getInstance().getProject();
		if (project != null && project.getPath() != null) return true;

		if (isGuest()) return false;

		return roles.contains(Roles.ADMIN) || roles.contains(role);
	}

	private static final Map<String, Boolean> writePermissionCache = new HashMap<>();
	private static final Map<String, Boolean> readPermissionCache = new HashMap<>();

	/**
	 * This method checks if the user is authorized to edit a given resource (workspace, subject, project).
	 * @param id workspace, subject or project id.
	 * @return true if user has write permissions.
	 */
	public static boolean hasWritePermission(String id) {
		if (!getAuthType().shouldPrompt()) {
			return false;
		}

		if (hasRole(Roles.ADMIN)) {
			return true;
		}

		if (writePermissionCache.containsKey(id)) {
			return writePermissionCache.get(id);
		}

		var response = get("/api/v0/auth/write/" + e(id));

		if (response.isEmpty()) {
			return false;
		}

		var result = Boolean.parseBoolean(response.get().body());
		writePermissionCache.put(id, result);

		return result;
	}

	public static boolean hasReadPermission(String id) {
		if (readPermissionCache.containsKey(id)) {
			return readPermissionCache.get(id);
		}

		if (hasRole(Roles.ADMIN)) {
			return true;
		}

		var response = get("/api/v0/auth/read/" + e(id));

		if (response.isEmpty()) {
			return false;
		}

		var result = Boolean.parseBoolean(response.get().body());
		readPermissionCache.put(id, result);

		return result;
	}

	/* Master server API */

	public static List<Server> fetchPublicServers() {
		var response = get("/api/servers", MASTER_SERVER);

		if (isInvalidResponse(response)) {
			return Collections.emptyList();
		} else {
			return List.of(GsonTools.getInstance().fromJson(response.get().body(), Server[].class));
		}
	}

	/* Server configuration */

	public static ServerConfiguration getServerConfiguration() {
		var response = get("/api/v0/server");

		if (isInvalidResponse(response)) {
			throw new HttpException("Error while fetching server configuration.");
		} else {
			Gson gson = GsonTools.getDefaultBuilder().registerTypeAdapter(Version.class, new VersionAdapter()).create();
			return gson.fromJson(response.get().body(), ServerConfiguration.class);
		}
	}

	/* Authentication */

	public static boolean login(String username, String password) {
		setCredentials(username, password);

		var response = get("/api/v0/auth/login");

		if (isInvalidResponse(response)) {
			setCredentials(null, null);
			return false;
		}

		setUser(GsonTools.getInstance().fromJson(response.get().body(), ExternalUser.class));

		return true;
	}

	public static boolean validate(String token) {
		setToken(token);

		var response = get("/api/v0/auth/verify");

		if (isInvalidResponse(response)) {
			setToken(null);
			return false;
		}

		setUser(GsonTools.getInstance().fromJson(response.get().body(), ExternalUser.class));

		return true;
	}

	public static void logout() {
		setToken(null);
		setCredentials(null, null);
		setUserOrganizationId(null);
		setUserId(null);
		setOrganizationId(null);
		roles.clear();
		writePermissionCache.clear();
		readPermissionCache.clear();
	}

	/* Password */

	public static boolean requestPasswordResetToken(String email) {
		var response = post("/api/v0/password/recovery",
			Map.of(
				"email", email
			)
		);

		return !isInvalidResponse(response);
	}

	public static boolean resetPassword(String token, String password) {
		var response = post("/api/v0/password/set/" + e(token),
			Map.of(
				"password", password
			)
		);

		return !isInvalidResponse(response);
	}

	/* Users */

	public static List<ExternalUser> getAllUsers() {
		var response = get("/api/v0/users");

		// TODO: 401 response is technically not an invalid response.
		if (isInvalidResponse(response)) {
			return Collections.emptyList();
		}

		return List.of(GsonTools.getInstance().fromJson(response.get().body(), ExternalUser[].class));
	}

	public static boolean editUser(String id, Map<String, Object> data) {
		var response = patch("/api/v0/users/" + e(id), data);

		return !isInvalidResponse(response);
	}

	public static boolean deleteUser(String id) {
		var response = delete("/api/v0/users/" + e(id));

		return !isInvalidResponse(response);
	}

	public static Optional<ExternalUser> createUser(String password, String email, String name, String organizationId) {
		var response = post(
			"/api/v0/users/",
			Map.of(
				"password", password,
				"email", email,
				"name", name,
				"organization", organizationId
			)
		);

		if (isInvalidResponse(response)) {
			return returnEmptyAndShowAnyErrorMessage(response);
		} else {
			return Optional.of(GsonTools.getInstance().fromJson(response.get().body(), ExternalUser.class));
		}
	}

	/* Subjects */

	public static Result createSubject(ExternalWorkspace workspace, String name) {
		return createSubject(workspace.getId(), name);
	}

	public static Result createSubject(String workspaceId, String name) {
		var response = post(
			"/api/v0/subjects",
			Map.of(
				"workspace-id", workspaceId,
				"subject-name", name
			)
		);

		return isInvalidResponse(response) ? Result.FAIL : Result.OK;
	}

	public static Result deleteSubject(String subjectId) {
		var response = delete("/api/v0/subjects/" + e(subjectId));

		return isInvalidResponse(response) ? Result.FAIL : Result.OK;
	}

	public static Result renameSubject(String subjectId, String newName) {
		var response = patch(
			"/api/v0/subjects/" + e(subjectId),
			Map.of(
				"subject-name", newName
			)
		);

		return isInvalidResponse(response) ? Result.FAIL : Result.OK;
	}

	/* Projects */

	public static Optional<String> downloadProject(String id) {
		String path;

		if (id.contains(":")) {
			String[] parts = id.split(":");
			path = "/api/v0/projects/" + e(parts[0]) + "?timestamp=" + parts[1];
		} else {
			path = "/api/v0/projects/" + e(id);
		}

		var response = get(path);

		if (isInvalidResponse(response)) {
			return Optional.empty();
		}

		return Optional.of(response.get().body());
	}

	public static Result uploadProject(String projectId, String projectData) {
		var response = post(
			"/api/v0/projects/" + e(projectId),
			Map.of(
				"project-data", projectData
			)
		);

		return isInvalidResponse(response) ? Result.FAIL : Result.OK;
	}

	public static Optional<String> createPersonalProject(String projectName) {
		var response = post(
			"/api/v0/projects?personal",
			Map.of(
				"project-name", projectName
			)
		);

		if (isInvalidResponse(response)) {
			return Optional.empty();
		}

		return Optional.of(response.get().body());
	}

	/**
	 * Creates a new project and places it inside given subject.
	 * @param subjectId subject Id, null if personal project.
	 * @param projectName name of the project.
	 * @return Result.OK if success else Result.FAIL.
	 */
	public static Result createProject(String subjectId, String projectName) {
		var response = post(
			"/api/v0/projects",
			Map.of(
				"subject-id", subjectId == null ? "personal" : subjectId,
				"project-name", projectName
			)
		);

		return isInvalidResponse(response) ? Result.FAIL : Result.OK;
	}

	public static Result editProject(String projectId, String name, String description) {
		var response = patch(
			"/api/v0/projects/" + e(projectId),
			Map.of(
				"name", name,
				"description", description
			)
		);

		return isInvalidResponse(response) ? Result.FAIL : Result.OK;
	}

	public static Result deleteProject(String projectName) {
		var response = delete("/api/v0/projects/" + e(projectName));

		return isInvalidResponse(response) ? Result.FAIL : Result.OK;
	}

	public static Result setProjectHidden(String projectId, boolean hidden) {
		var response = patch(
			"/api/v0/projects/" + e(projectId),
			Map.of("hidden", hidden)
		);

		return isInvalidResponse(response) ? Result.FAIL : Result.OK;
	}

	public static String getCKEditorUploadUrl() {
		return host.resolve("/api/v0/upload/ckeditor").toString();
	}

	/* Slides */

	public static List<ExternalSlide> getAllSlides() {
		var response = get("/api/v0/slides/");

		if (isInvalidResponse(response)) {
			return Collections.emptyList();
		}

		return List.of(GsonTools.getInstance().fromJson(response.get().body(), ExternalSlide[].class));
	}

	public static Optional<JsonObject> getSlideProperties(URI uri) {
		return getSlideProperties(uri.getPath().substring(1), uri);
	}

	public static Optional<JsonObject> getSlideProperties(String id) {
		return getSlideProperties(id, host);
	}

	public static Optional<JsonObject> getSlideProperties(String id, URI host) {
		var response = get("/api/v0/slides/" + id, host);

		if (isInvalidResponse(response)) {
			return Optional.empty();
		}

		JsonObject slides = JsonParser.parseString(response.get().body()).getAsJsonObject();
		return Optional.of(slides);
	}

	public static Result editSlide(String slideId, String name) {
		var response = patch(
			"/api/v0/slides/" + e(slideId),
			Map.of("slide-name", name)
		);

		return isInvalidResponse(response) ? Result.FAIL : Result.OK;
	}

	public static Result deleteSlide(String slideId) {
		var response = delete("/api/v0/slides/" + e(slideId));

		return isInvalidResponse(response) ? Result.FAIL : Result.OK;
	}

	public static Result submitSlideForTiling(String slideId) {
		var response = post("/api/v0/slides/" + e(slideId) + "/tile", Map.of());

		return isInvalidResponse(response) ? Result.FAIL : Result.OK;
	}

	public static Result uploadSlideChunk(String fileName, long fileSize, byte[] buffer, int chunkSize, int chunkIndex) throws IOException, InterruptedException {
		String boundary = new BigInteger(256, new Random()).toString();
		Map<Object, Object> data = new LinkedHashMap<>();
		data.put("file", buffer);

		HttpClient client = getHttpClient();
		HttpRequest.Builder builder = HttpRequest.newBuilder()
			.uri(getSlideUploadURL(fileName, fileSize, chunkIndex, chunkSize))
			.POST(ofMimeMultipartData(data, boundary))
			.header("Content-Type", "multipart/form-data;boundary=" + boundary);

		addAuthorization(builder);
		HttpRequest request = builder.build();

		HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
		return isInvalidResponse(Optional.of(response)) ? Result.FAIL : Result.OK;
	}

	public static URI getSlideUploadURL(String fileName, long fileSize, int chunkIndex, int chunkSize) {
		return host.resolve(String.format(
			"/api/v0/slides/?filename=%s&fileSize=%s&chunk=%s&chunkSize=%s",
			e(fileName),
			fileSize,
			chunkIndex,
			chunkSize
		));
	}

	/**
	 * Formats the URI by replacing placeholders with proper values.
	 * @return formatted string as a URI.
	 */
	public static URI getRenderRegionURL(String uri, String slideId, int tileX, int tileY, int level, int tileWidth, int tileHeight, int depth) {
		return URI.create(uri
				.replace("{slideId}", e(slideId))
				.replace("{tileX}", String.valueOf(tileX))
				.replace("{tileY}", String.valueOf(tileY))
				.replace("{level}", String.valueOf(level))
				.replace("{tileWidth}", String.valueOf(tileWidth))
				.replace("{tileHeight}", String.valueOf(tileHeight))
				.replace("{depth}", String.valueOf(depth))
		);
	}

	/* Workspaces */

	public static Optional<ExternalWorkspace> getWorkspace(String id) {
		var response = get("/api/v0/workspaces/" + e(id));

		if (isInvalidResponse(response)) {
			return Optional.empty();
		}

		return Optional.of(GsonTools.getInstance().fromJson(response.get().body(), ExternalWorkspace.class));
	}

	public static List<ExternalWorkspace> getAllWorkspaces() {
		var response = get("/api/v0/workspaces");

		if (isInvalidResponse(response)) {
			throw new HttpException("Error while fetching workspaces.");
		}

		return List.of(GsonTools.getInstance().fromJson(response.get().body(), ExternalWorkspace[].class));
	}

	public static Result createWorkspace(String workspaceName) {
		var response = post(
			"/api/v0/workspaces",
			Map.of("workspace-name", workspaceName)
		);

		return isInvalidResponse(response) ? Result.FAIL : Result.OK;
	}

	public static Result renameWorkspace(String workspaceId, String newName) {
		var response = patch(
			"/api/v0/workspaces/" + e(workspaceId),
			Map.of("workspace-name", newName)
		);

		return isInvalidResponse(response) ? Result.FAIL : Result.OK;
	}

	public static Result editWorkspaceWritePermissions(String workspaceId, List<ExternalOwner> owners) {
		var response = patch(
			"/api/v0/workspaces/" + e(workspaceId),
			Map.of("write", GsonTools.getInstance().toJson(owners.stream().map(ExternalOwner::getId).collect(Collectors.toList())))
		);

		return isInvalidResponse(response) ? Result.FAIL : Result.OK;
	}

	public static Result editWorkspaceReadPermissions(String workspaceId, List<ExternalOwner> owners) {
		var response = patch(
			"/api/v0/workspaces/" + e(workspaceId),
			Map.of("read", GsonTools.getInstance().toJson(owners.stream().map(ExternalOwner::getId).collect(Collectors.toList())))
		);

		return isInvalidResponse(response) ? Result.FAIL : Result.OK;
	}

	public static Result deleteWorkspace(String workspaceId) {
		var response = delete("/api/v0/workspaces/" + e(workspaceId));

		if (response.isEmpty()) {
			return Result.FAIL;
		}

		return isInvalidResponse(response) ? Result.FAIL : Result.OK;
	}

	/* Organizations */

	public static Optional<List<ExternalOrganization>> getAllOrganizations() {
		var response = get("/api/v0/organizations");

		if (isInvalidResponse(response)) {
			return Optional.empty();
		}

		return Optional.of(List.of(GsonTools.getInstance().fromJson(response.get().body(), ExternalOrganization[].class)));
	}

	public static Optional<ExternalOrganization> createOrganization(String name) {
		var response = post(
			"/api/v0/organizations",
			Map.of("name", name)
		);

		if (isInvalidResponse(response)) {
			return Optional.empty();
		} else {
			return Optional.of(GsonTools.getInstance().fromJson(response.get().body(), ExternalOrganization.class));
		}
	}

	public static Result deleteOrganization(String id) {
		var response = delete("/api/v0/organizations/" + e(id));

		return isInvalidResponse(response) ? Result.FAIL : Result.OK;
	}

	public static boolean editOrganization(String id, String name, File logo) {
		try {
			String boundary = new BigInteger(256, new Random()).toString();
			Map<Object, Object> data = new LinkedHashMap<>();
			data.put("name", name);

			if (logo != null) {
				data.put("logo", Files.readAllBytes(logo.toPath()));
			}

			HttpClient client = getHttpClient();
			HttpRequest.Builder builder = HttpRequest.newBuilder()
					.uri(host.resolve("/api/v0/organizations/" + e(id)))
					.method("PATCH", ofMimeMultipartData(data, boundary))
					.header("Content-Type", "multipart/form-data;boundary=" + boundary);

			addAuthorization(builder);
			HttpRequest request = builder.build();

			HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

			return !(isInvalidResponse(Optional.of(response)));
		} catch (IOException | InterruptedException e) {
			logger.error("Error while uploading organization logo", e);
		}

		return false;
	}

	/* Backups */

	public static Optional<List<ExternalBackup>> getAllBackups() {
		var response = get("/api/v0/backups");

		if (isInvalidResponse(response)) {
			return Optional.empty();
		}

		return Optional.of(List.of(GsonTools.getInstance().fromJson(response.get().body(), ExternalBackup[].class)));
	}

	public static boolean restoreBackup(String backup, String timestamp) {
		var response = get("/api/v0/backups/restore/" + e(backup) + "/" + e(timestamp));

		return !isInvalidResponse(response);
	}

	/* Private API */

	private static boolean isInvalidResponse(Optional<HttpResponse<String>> response) {
		var isInvalidResponse = response.isEmpty() || (response.get().statusCode() < 200 || response.get().statusCode() > 300);

		if (isInvalidResponse) {
			if (response.isPresent()) {
				logger.error("Invalid HTTP Response: {}", httpResponseToString(response.get()));
			} else {
				logger.error("Invalid HTTP Response: Response was empty.");
			}
		}

		return isInvalidResponse;
	}

	private static Optional<HttpResponse<String>> get(String path) {
		return get(path, host);
	}

	private static Optional<HttpResponse<String>> get(String path, URI host) {
		try {
			HttpClient client = getHttpClient();
			HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(host.resolve(path));

			addAuthorization(builder);
			HttpRequest request = builder.build();

			return Optional.of(client.send(request, BodyHandlers.ofString()));
		} catch (IOException | InterruptedException e) {
			logger.error("Error when making HTTP GET request", e);
			throw new HttpException(e);
		}
	}

	private static Optional<HttpResponse<String>> post(String path, Map<Object, Object> data) {
		try {
			HttpClient client = getHttpClient();
			HttpRequest.Builder builder = HttpRequest.newBuilder()
				.POST(ofFormData(data))
				.uri(host.resolve(path))
				.header("Content-Type", "application/x-www-form-urlencoded");

			addAuthorization(builder);
			HttpRequest request = builder.build();

			return Optional.of(client.send(request, BodyHandlers.ofString()));
		} catch (Exception e) {
			logger.error("Error when making HTTP POST request", e);
			throw new HttpException(e);
		}
	}

	private static Optional<HttpResponse<String>> delete(String path) {
		try {
			HttpClient client = getHttpClient();
			HttpRequest.Builder builder = HttpRequest.newBuilder()
				.DELETE()
				.uri(host.resolve(path));

			addAuthorization(builder);
			HttpRequest request = builder.build();

			return Optional.of(client.send(request, BodyHandlers.ofString()));
		} catch (IOException | InterruptedException e) {
			logger.error("Error when making HTTP DELETE request", e);
			throw new HttpException(e);
		}
	}

	private static Optional<HttpResponse<String>> put(String path, Map<?, ?> data) {
		return putOrPatch(path, data, "PUT");
	}

	private static Optional<HttpResponse<String>> patch(String path, Map<?, ?> data) {
		return putOrPatch(path, data, "PATCH");
	}

	private static Optional<HttpResponse<String>> putOrPatch(String path, Map<?, ?> data, String method) {
		try {
			HttpClient client = getHttpClient();
			HttpRequest.Builder builder = HttpRequest.newBuilder()
					.method(method, ofFormData((Map<Object, Object>) data))
					.uri(host.resolve(path))
					.header("Content-Type", "application/x-www-form-urlencoded");

			addAuthorization(builder);

			HttpRequest request = builder.build();

			return Optional.of(client.send(request, BodyHandlers.ofString()));
		} catch (IOException | InterruptedException e) {
			logger.error("Error when making HTTP " + method + " request", e);
			throw new HttpException(e);
		}
	}

	private static void addAuthorization(HttpRequest.Builder builder) {
		if (getAuthType() == AuthType.USERNAME) {
			builder.headers("Authorization", basicAuth(username, password));
		} else if (getAuthType() == AuthType.TOKEN) {
			builder.headers("Token", getToken());
		}
	}

	private static HttpClient getHttpClient() {
		return HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.version(HttpClient.Version.HTTP_1_1)
				.build();
	}

	private static HttpRequest.BodyPublisher ofFormData(Map<Object, Object> data) {
		var builder = new StringBuilder();

		for (Map.Entry<Object, Object> entry : data.entrySet()) {
			if (builder.length() > 0) {
				builder.append("&");
			}

			builder.append(e(entry.getKey().toString()));
			builder.append("=");

			if (entry.getValue() instanceof byte[]) {
				builder.append(e(new String((byte[]) entry.getValue())));
			} else {
				builder.append(e(entry.getValue().toString()));
			}
		}

		return HttpRequest.BodyPublishers.ofString(builder.toString());
	}

	private static final String LINE_FEED = "\r\n";

	private static HttpRequest.BodyPublisher ofMimeMultipartData(Map<Object, Object> data, String boundary) throws IOException {
		var byteArrays = new ArrayList<byte[]>();
		byte[] separator = ("--" + boundary + LINE_FEED + "Content-Disposition: form-data; name=").getBytes();

		for (Map.Entry<Object, Object> entry : data.entrySet()) {
			byteArrays.add(separator);

			if (entry.getValue() instanceof Path path) {
				String mimeType = Files.probeContentType(path);
				byteArrays.add(("\"" + entry.getKey() + "\"; filename=\"" + path.getFileName() + "\""
						+ LINE_FEED + "Content-Type: " + mimeType).getBytes());
				byteArrays.add((LINE_FEED + LINE_FEED).getBytes());
				byteArrays.add(Files.readAllBytes(path));
				byteArrays.add(LINE_FEED.getBytes());
			} else if (entry.getValue() instanceof byte[]) {
				byteArrays.add(("\"" + entry.getKey() + "\"; filename=\"unnamed\""
						+ LINE_FEED + "Content-Type: application/octet-stream").getBytes());
				byteArrays.add((LINE_FEED + LINE_FEED).getBytes());
				byteArrays.add((byte[]) entry.getValue());
				byteArrays.add(LINE_FEED.getBytes());
			} else {
				byteArrays.add(("\"" + entry.getKey() + "\"").getBytes());
				byteArrays.add((LINE_FEED + LINE_FEED).getBytes());
				byteArrays.add((entry.getValue() + LINE_FEED).getBytes());
			}
		}

		byteArrays.add(("--" + boundary + "--").getBytes());
		return HttpRequest.BodyPublishers.ofByteArrays(byteArrays);
	}

	/**
	 * TODO: This is temporary, pending whole rewrite of EduAPI.
	 */
	private static <K> Optional<K> returnEmptyAndShowAnyErrorMessage(Optional<HttpResponse<String>> response) {
		try {
			var error = GsonTools.getInstance().fromJson(response.get().body(), ExternalError.class);

			Dialogs.showErrorNotification("Error", error.getError());
		} catch (JsonParseException | NoSuchElementException ignored) {}

		return Optional.empty();
	}

	/**
	 * Encodes given string with UTF-8. Java URLEncoder changes spaces into +
	 * @param toEncode plain string
	 * @return An encoded URL valid for HTTP
	 */
	public static String e(String toEncode) {
		return URLEncoder.encode(toEncode, StandardCharsets.UTF_8).replace("+", "%20");
	}

	public static String d(String toDecode) {
		return URLDecoder.decode(toDecode, StandardCharsets.UTF_8);
	}

	private static String basicAuth(String username, String password) {
		return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
	}

	private static String httpResponseToString(HttpResponse<String> httpResponse) {
		Map<String, List<String>> headers = Maps.newHashMap(httpResponse.request().headers().map());

		if (headers.containsKey("Token")) {
			headers.put("Token", List.of("Removed from log"));
		}

		if (headers.containsKey("Authorization")) {
			headers.put("Authorization", List.of("Removed from log"));
		}

		return String.format("[Path: %s, Method: %s, Request Headers: %s, Status: %d, Response Headers: %s, Body: %s]",
				httpResponse.request().uri().getPath(),
				httpResponse.request().method(),
				headers,
				httpResponse.statusCode(),
				httpResponse.headers().map(),
				httpResponse.body());
	}

	public enum Result {
		OK,
		FAIL
	}

	public enum AuthType {

		UNAUTHENTICATED(false),
		GUEST(false),
		USERNAME(true),
		TOKEN(true);

		/**
		 * Represents if the should be prompted for various different questions, such as Image Type.
		 * AuthTypes without any write access should return false.
		 */
		private final boolean prompt;

		AuthType(boolean prompt) {
			this.prompt = prompt;
		}

		public boolean shouldPrompt() {
			return prompt;
		}
	}
}

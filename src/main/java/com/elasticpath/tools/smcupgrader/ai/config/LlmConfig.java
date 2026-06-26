package com.elasticpath.tools.smcupgrader.ai.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Machine-wide configuration for the CLI LLM invoked by AI Assist Mode.
 *
 * <p>Loaded from {@code ~/.smc-upgrader.json}, where the settings are nested under an {@code llm} object:</p>
 *
 * <pre>
 * {
 *   "llm": {
 *     "command": "{executable} {permissions} --model sonnet {prompt}",
 *     "executable": "claude",
 *     "skipPermissionsArg": "--dangerously-skip-permissions"
 *   }
 * }
 * </pre>
 *
 * <p>When the file is absent, or any field is unspecified, the defaults reproduce the historical
 * Claude Code behavior exactly.</p>
 */
public class LlmConfig {

	/** Default invocation template. */
	public static final String DEFAULT_COMMAND = "{executable} {permissions} --model sonnet {prompt}";

	/** Default executable (binary name). */
	public static final String DEFAULT_EXECUTABLE = "claude";

	/** Default skip-permissions argument. */
	public static final String DEFAULT_SKIP_PERMISSIONS_ARG = "--dangerously-skip-permissions";

	/** Placeholder substituted with the executable value. */
	public static final String EXECUTABLE_PLACEHOLDER = "{executable}";

	/** Placeholder substituted with the (shell-escaped) prompt. Required in every command template. */
	public static final String PROMPT_PLACEHOLDER = "{prompt}";

	private static final Logger LOGGER = LoggerFactory.getLogger(LlmConfig.class);
	private static final String CONFIG_FILE_NAME = ".smc-upgrader.json";

	private String command;
	private String executable;
	private String skipPermissionsArg;

	/**
	 * Default constructor for JSON deserialization.
	 */
	public LlmConfig() {
	}

	/**
	 * Constructor.
	 *
	 * @param command            the invocation template
	 * @param executable         the executable (binary name)
	 * @param skipPermissionsArg the argument substituted when skip-permissions is active
	 */
	public LlmConfig(final String command, final String executable, final String skipPermissionsArg) {
		this.command = command;
		this.executable = executable;
		this.skipPermissionsArg = skipPermissionsArg;
	}

	/**
	 * Wrapper matching the JSON file structure, where settings are nested under an {@code llm} object.
	 */
	private static final class ConfigFile {
		private LlmConfig llm;
	}

	/**
	 * Load the configuration from {@code ~/.smc-upgrader.json}, resolved via the {@code user.home} system property.
	 *
	 * @return the loaded configuration, with defaults applied for any missing file or field
	 */
	public static LlmConfig load() {
		return load(new File(System.getProperty("user.home"), CONFIG_FILE_NAME));
	}

	/**
	 * Load the configuration from the given file.
	 *
	 * @param configFile the configuration file
	 * @return the loaded configuration, with defaults applied for any missing file or field
	 * @throws LlmConfigException if the configured command is invalid (e.g. missing the {@code {prompt}} placeholder)
	 */
	public static LlmConfig load(final File configFile) {
		LlmConfig config = readConfig(configFile);
		validate(config, configFile);
		return config;
	}

	private static LlmConfig readConfig(final File configFile) {
		if (configFile == null || !configFile.isFile()) {
			return withDefaults(new LlmConfig());
		}

		try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(configFile.toPath()), StandardCharsets.UTF_8)) {
			Gson gson = new Gson();
			ConfigFile configWrapper = gson.fromJson(reader, ConfigFile.class);
			LlmConfig llmConfig = configWrapper == null ? null : configWrapper.llm;
			return withDefaults(llmConfig == null ? new LlmConfig() : llmConfig);
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Could not read LLM configuration from {}. Using defaults.", configFile.getAbsolutePath(), e);
			return withDefaults(new LlmConfig());
		}
	}

	/**
	 * Validate a resolved configuration, failing fast on misconfiguration.
	 *
	 * @param config     the resolved configuration
	 * @param configFile the source file (for the error message; may be null)
	 * @throws LlmConfigException if the command does not contain the {@code {prompt}} placeholder
	 */
	private static void validate(final LlmConfig config, final File configFile) {
		if (!config.command.contains(PROMPT_PLACEHOLDER)) {
			String location = configFile == null ? CONFIG_FILE_NAME : configFile.getAbsolutePath();
			throw new LlmConfigException("Invalid LLM configuration in " + location
					+ ": the 'command' must contain the " + PROMPT_PLACEHOLDER + " placeholder, "
					+ "which is replaced with the prompt sent to the LLM.");
		}
	}

	/**
	 * Apply per-field defaults for any null or blank field.
	 *
	 * @param config the configuration to default in place
	 * @return the same configuration instance, with defaults applied
	 */
	private static LlmConfig withDefaults(final LlmConfig config) {
		if (isBlank(config.command)) {
			config.command = DEFAULT_COMMAND;
		}
		if (isBlank(config.executable)) {
			config.executable = DEFAULT_EXECUTABLE;
		}
		if (isBlank(config.skipPermissionsArg)) {
			config.skipPermissionsArg = DEFAULT_SKIP_PERMISSIONS_ARG;
		}
		return config;
	}

	private static boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	/**
	 * Resolve the target of the {@code which} availability check.
	 *
	 * <p>This is the {@code executable} value when the command uses the {@code {executable}} placeholder, otherwise the
	 * first whitespace-delimited token of the command (since an omitted placeholder means the command begins with the
	 * literal binary name).</p>
	 *
	 * @return the executable name to check for availability
	 */
	public String resolveAvailabilityTarget() {
		if (command.contains(EXECUTABLE_PLACEHOLDER)) {
			return executable;
		}
		String trimmed = command.trim();
		int firstSpace = trimmed.indexOf(' ');
		return firstSpace == -1 ? trimmed : trimmed.substring(0, firstSpace);
	}

	/**
	 * Get the invocation template.
	 *
	 * @return the command template
	 */
	public String getCommand() {
		return command;
	}

	/**
	 * Get the executable (binary name).
	 *
	 * @return the executable
	 */
	public String getExecutable() {
		return executable;
	}

	/**
	 * Get the argument substituted when skip-permissions is active.
	 *
	 * @return the skip-permissions argument
	 */
	public String getSkipPermissionsArg() {
		return skipPermissionsArg;
	}
}

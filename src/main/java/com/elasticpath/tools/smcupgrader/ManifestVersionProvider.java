package com.elasticpath.tools.smcupgrader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import picocli.CommandLine;

/**
 * Provides the application version string for picocli's {@code --version} option,
 * sourced from {@code smc-upgrader.properties} on the classpath. The properties
 * file is populated at build time by Maven resource filtering using
 * {@code ${project.version}} from {@code pom.xml}.
 */
public class ManifestVersionProvider implements CommandLine.IVersionProvider {

	private static final String PROPERTIES_RESOURCE = "smc-upgrader.properties";
	private static final String VERSION_KEY = "version";
	private static final String UNKNOWN_VERSION = "unknown";

	@Override
	public String[] getVersion() throws IOException {
		final Properties properties = new Properties();
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(PROPERTIES_RESOURCE)) {
			if (input != null) {
				properties.load(input);
			}
		}
		return new String[]{"smc-upgrader " + properties.getProperty(VERSION_KEY, UNKNOWN_VERSION)};
	}
}

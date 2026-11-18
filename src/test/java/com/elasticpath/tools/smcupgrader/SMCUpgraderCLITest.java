/*	Copyright 2026 Elastic Path Software Inc.

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
*/

package com.elasticpath.tools.smcupgrader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/**
 * Tests for {@link SMCUpgraderCLI} option parsing.
 */
class SMCUpgraderCLITest {
	private static final String VERSION = "8.6.x";
	private static final String REVERT_PATCHES = "--revert-patches";

	@Test
	void testRevertPatches_disabledByDefault() {
		assertThat(parseRevertPatches(VERSION)).isFalse();
	}

	@Test
	void testRevertPatches_enabledWithFlag() {
		assertThat(parseRevertPatches(REVERT_PATCHES, VERSION)).isTrue();
	}

	@Test
	void testRevertPatches_disabledWithNegatedFlag() {
		assertThat(parseRevertPatches("--no-revert-patches", VERSION)).isFalse();
	}

	@Test
	void testRevertPatches_shortFormRemoved() {
		assertThatThrownBy(() -> newCommandLine().parseArgs("-p", VERSION))
				.isInstanceOf(CommandLine.UnmatchedArgumentException.class)
				.hasMessageContaining("Unknown option: '-p'");
	}

	private boolean parseRevertPatches(final String... args) {
		final CommandLine commandLine = newCommandLine();
		commandLine.parseArgs(args);
		return commandLine.getCommandSpec().findOption(REVERT_PATCHES).getValue();
	}

	private CommandLine newCommandLine() {
		return new CommandLine(new SMCUpgraderCLI()).setToggleBooleanFlags(true);
	}
}

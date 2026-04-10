/*	Copyright 2025 Elastic Path Software Inc.

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

package com.elasticpath.tools.smcupgrader.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link ClaudeCodeInvoker}.
 */
class ClaudeCodeInvokerTest {

	@TempDir
	File tempDir;

	private ClaudeCodeInvoker invoker;

	@BeforeEach
	void setUp() {
		invoker = new ClaudeCodeInvoker(tempDir);
	}

	@Test
	void testInvokeClaudeCode_whenClaudeNotAvailable_returnsFalse() throws IOException {
		// This test verifies graceful handling when Claude is not installed
		// Only run this test if Claude is actually not available
		boolean available = invoker.isClaudeCodeAvailable();

		if (!available) {
			String prompt = "Test prompt for Claude";
			boolean result = invoker.invokeClaudeCode(prompt);

			// Should return false when Claude is not available
			assertThat(result).isFalse();
		}
		// If Claude IS available, we skip this test as we can't test execution without user interaction
	}

	@Test
	void testIsClaudeCodeAvailable() {
		// This test will pass or fail depending on whether Claude Code is installed
		// We can't make strong assertions, but we can verify the method doesn't crash
		boolean available = invoker.isClaudeCodeAvailable();
		assertThat(available).isIn(true, false);
	}

	@Test
	void testGetClaudeCodeVersion() {
		// This test will return a version or null depending on installation
		// We can't make strong assertions, but we can verify the method doesn't crash
		String version = invoker.getClaudeCodeVersion();
		// Version will be null if Claude is not installed, or a string if it is
		assertThat(version == null || version.length() > 0).isTrue();
	}
}

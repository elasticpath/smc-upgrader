package com.elasticpath.tools.smcupgrader.astgrep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link VersionBucket}.
 */
class VersionBucketTest {

	@Test
	void parseValidBucket() {
		VersionBucket bucket = VersionBucket.parse("8.7.x");
		assertThat(bucket.toString()).isEqualTo("8.7.x");
	}

	@Test
	void parseInvalidBucketTwoParts() {
		assertThatThrownBy(() -> VersionBucket.parse("9.0"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("8.7.x");
	}

	@Test
	void parseInvalidBucketSinglePart() {
		assertThatThrownBy(() -> VersionBucket.parse("8"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("8.7.x");
	}

	@Test
	void parseInvalidBucketNonNumeric() {
		assertThatThrownBy(() -> VersionBucket.parse("abc.def.x"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("8.7.x");
	}

	@Test
	void tryParseValid() {
		VersionBucket bucket = VersionBucket.tryParse("8.7.x");
		assertThat(bucket).isNotNull();
		assertThat(bucket.toString()).isEqualTo("8.7.x");
	}

	@Test
	void tryParseInvalid() {
		assertThat(VersionBucket.tryParse("not-a-version")).isNull();
	}

	@Test
	void tryParseSinglePart() {
		assertThat(VersionBucket.tryParse("8")).isNull();
	}

	@Test
	void compareToSameMajorDifferentMinor() {
		VersionBucket v86 = VersionBucket.parse("8.6.x");
		VersionBucket v87 = VersionBucket.parse("8.7.x");
		assertThat(v86.compareTo(v87)).isLessThan(0);
		assertThat(v87.compareTo(v86)).isGreaterThan(0);
	}

	@Test
	void compareToDifferentMajor() {
		VersionBucket v87 = VersionBucket.parse("8.7.x");
		VersionBucket v90 = VersionBucket.parse("9.0.x");
		assertThat(v87.compareTo(v90)).isLessThan(0);
		assertThat(v90.compareTo(v87)).isGreaterThan(0);
	}

	@Test
	void compareToEqual() {
		VersionBucket a = VersionBucket.parse("8.7.x");
		VersionBucket b = VersionBucket.parse("8.7.x");
		assertThat(a.compareTo(b)).isEqualTo(0);
	}

	@Test
	void toStringPreservesRaw() {
		assertThat(VersionBucket.parse("8.7.x").toString()).isEqualTo("8.7.x");
		assertThat(VersionBucket.parse("9.0.x").toString()).isEqualTo("9.0.x");
		assertThat(VersionBucket.parse("10.1.x").toString()).isEqualTo("10.1.x");
	}
}

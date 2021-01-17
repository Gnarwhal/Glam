plugins {
	kotlin("multiplatform") version "1.4.21"
}

kotlin {
	linuxX64("linux") {
		binaries {
			executable()
		}
	}
	mingwX64("windows") {
		binaries {
			executable()
		}
	}
	macosX64("macos") {
		binaries {
			executable()
		}
	}
}

tasks.withType<Wrapper> {
	gradleVersion = "6.7.1"
	distributionType = Wrapper.DistributionType.BIN
}

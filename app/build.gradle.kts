plugins {
	alias(libs.plugins.android.application)
}

android {
	namespace = "com.sqp.percent98"
	compileSdk {
		version = release(37)
	}

	defaultConfig {
		applicationId = "com.sqp.percent98"
		minSdk = 31
		targetSdk = 37
		versionCode = 107
		versionName = "1.07"

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	buildTypes {
		release {
			optimization {
				enable = true
			}
		}

		create("releases") {
			initWith(getByName("release"))
		}
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
}

androidComponents {
	onVariants {
			variant ->
		variant.outputs.forEach { output ->
			output.outputFileName.set(
				"98percent.${output.versionName.get()}.apk"
			)
		}
	}
}

dependencies {
	implementation(libs.androidx.appcompat)
	implementation(libs.androidx.core.ktx)
	implementation(libs.material)
	testImplementation(libs.junit)
	androidTestImplementation(libs.androidx.espresso.core)
	androidTestImplementation(libs.androidx.junit)
	implementation(libs.colorpicker)
}
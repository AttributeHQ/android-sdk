plugins {
    id("com.android.library")
    kotlin("android")
    id("maven-publish")
}

android {
    namespace = "com.attributehq.sdk"
    compileSdk = 34
    publishing { singleVariant("release") { withSourcesJar() } }
    testOptions { unitTests.isIncludeAndroidResources = true }

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("org.json:json:20231013")
    implementation("com.android.installreferrer:installreferrer:2.2")
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")
    implementation("androidx.core:core-ktx:1.12.0")
}

afterEvaluate {
    publishing {
        repositories {
            maven {
                name = "validation"
                url = uri(layout.buildDirectory.dir("validation-repository"))
            }
        }
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.attributehq"
                artifactId = "sdk"
                version = "1.1.0-beta.2"

                pom {
                    name.set("AttributeHQ Android SDK")
                    description.set("Lightweight Android attribution SDK for AttributeHQ")
                    url.set("https://attributehq.com")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                }
            }
        }
    }
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Optional pre-fetched runtime directory for reproducible offline validation.
tasks.withType<Test>().configureEach {
    providers.gradleProperty("evidenceFixtureOutput").orNull?.let { systemProperty("evidenceFixtureOutput", it) }
    providers.gradleProperty("robolectricDependencyDir").orNull?.let { directory ->
        systemProperty("robolectric.offline", "true")
        systemProperty("robolectric.dependency.dir", directory)
    }
}

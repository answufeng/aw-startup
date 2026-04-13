apply(plugin = "maven-publish")

extensions.configure<org.gradle.api.publish.PublishingExtension> {
    publications {
        register<org.gradle.api.publish.maven.MavenPublication>("release") {
            afterEvaluate {
                from(components["release"])
            }

            groupId = "com.github.answufeng"
            artifactId = "aw-startup"
            version = property("VERSION_NAME")?.toString() ?: "1.0.0"

            pom {
                name.set("aw-startup")
                description.set("Android app startup initialization library with priority and dependency awareness")
                url.set("https://github.com/answufeng/aw-startup")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                developers {
                    developer {
                        id.set("answufeng")
                        name.set("answufeng")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/answufeng/aw-startup.git")
                    url.set("https://github.com/answufeng/aw-startup")
                }
            }
        }
    }
}

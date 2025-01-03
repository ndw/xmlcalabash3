plugins {
    // Apply the foojay-resolver plugin to allow automatic download of JDKs
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "xmlcalabash"

include("xmlcalabash", "app", "test-driver",
        "documentation",
        "send-mail",
        "paged-media:weasyprint", "paged-media:prince",
        "paged-media:antenna-house", "paged-media:fop",
        "ext:unique-id", "ext:metadata-extractor", "ext:cache",
        "ext:pipeline-messages", "ext:epubcheck",
        "template:kotlin", "template:java",
    )

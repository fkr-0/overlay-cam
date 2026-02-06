plugins {
    // Root buildscript placeholder
}

tasks.register("cleanAll") {
    dependsOn(gradle.includedBuilds.map { it.task(":clean") })
}
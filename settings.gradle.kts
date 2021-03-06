rootProject.name = "katan-server"

pluginManagement {
    repositories {
        gradlePluginPortal()
        jcenter()
    }
}

include("katan-api")
include("katan-core")
include("katan-common")
include("katan-web-server")
include("katan-cli")
include("katan-bootstrap")
include("katan-database-jdbc")
include("katan-database")
include("katan-plugin-api")
include("katan-core-docker")
include("katan-database-api")
include("katan-database-mongodb")

import Dependencies._

ThisBuild / scalaVersion     := "2.13.8"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .enablePlugins(
    JavaAppPackaging,
    DockerPlugin
  )
  .settings(
    name := "kafka-drain",
    libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.6.19",
    libraryDependencies += "com.typesafe.akka" %% "akka-stream-kafka" % "3.0.0",
    libraryDependencies += scalaTest % Test
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.

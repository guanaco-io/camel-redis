/*
 * Copyright 2020 - anova r&d bvba
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import sbt.KeyRanks.ATask
import sbt.file

lazy val scala212 = "2.12.7"
lazy val scala211 = "2.11.7"
lazy val supportedScalaVersions = List(scala212, scala211)

ThisBuild / scalaVersion           := scala212
ThisBuild / version                := "1.0.x-SNAPSHOT"
ThisBuild / organization           := "io.guanaco.camel.redis"
ThisBuild / organizationName       := "Guanaco"

val commonSettings = Seq(
  publishMavenStyle      := true,
  bintrayCredentialsFile := Path.userHome / ".bintray" / ".credentials",
  bintrayOrganization    := Some("guanaco-io"),
  bintrayRepository      := "maven",
  bintrayOmitLicense     := true
)

lazy val root = (project in file("."))
  .enablePlugins(ScalafmtPlugin, JavaAppPackaging, SbtOsgi)
  .settings(commonSettings)
  .settings(
    name := "camel-redis",
    libraryDependencies ++= Dependencies.tests,
    crossScalaVersions := Nil,
    publish / skip := true
  )
  .aggregate(api, features, impl, testkit)


lazy val api = (project in file("api"))
  .enablePlugins(SbtOsgi)
  .settings(commonSettings)
  .settings(
    name := "api",
    description := "Guanaco Camel Redis public API",
    libraryDependencies ++= Dependencies.api,
    crossScalaVersions := supportedScalaVersions,
    osgiSettings,
    OsgiKeys.exportPackage := List("io.guanaco.camel.redis"),
    OsgiKeys.privatePackage := Nil,
    OsgiKeys.additionalHeaders := Map(
      "Bundle-Name" -> "Guanaco :: Camel Redis :: API",
    ),
  )

val packageXml = taskKey[File]("Produces an xml artifact.").withRank(ATask)
val generateFeatures = taskKey[Unit]("Generates the features files.")

lazy val features = (project in file("features"))
  .settings(commonSettings)
  .settings(
    generateFeatures := {
      streams.value.log.info("Generating features.xml files")
      val input = (resourceDirectory in Compile).value / "features.xml"
      val output = file("features") / "target" / "features.xml"
      IO.write(output, IO.read(input).replaceAll("\\$\\{version\\}", version.value))
    },

    publishM2 := (publishM2 dependsOn generateFeatures).value,
    publish := (publish dependsOn generateFeatures).value,

    name := "features",
    crossScalaVersions := supportedScalaVersions,

    // disable .jar publishing
    publishArtifact in (Compile, packageBin) := false,
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := false,
    packageXml := file("features") / "target" / "features.xml",
    addArtifact( Artifact("features", "features", "xml"), packageXml ).settings

  )

lazy val impl = (project in file("impl"))
  .enablePlugins(SbtOsgi)
  .settings(commonSettings)
  .settings(
    name := "impl",
    description := "Implementation for the Guanaco Camel Redis API",
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= Dependencies.impl,
    parallelExecution in Test := false,
    osgiSettings,
    OsgiKeys.importPackage := List("*"),
    OsgiKeys.privatePackage := List(s"${OsgiKeys.bundleSymbolicName.value}.*"),
    OsgiKeys.additionalHeaders := Map(
      "Bundle-Name" -> "Guanaco :: Camel Redis :: Implementation"
    )
  )
  .dependsOn(api)

lazy val testkit = (project in file("testkit"))
  .settings(commonSettings)
  .settings(
    name := "testkit",
    description := "Utilities for unit testing your own projects using this idempotency store",
    libraryDependencies ++= Dependencies.testkit,
    crossScalaVersions := supportedScalaVersions
  )
  .dependsOn(api, impl)

scalacOptions += "-feature"
testOptions in Test := Seq(Tests.Argument(TestFrameworks.JUnit, "-a"))

fork in run := true

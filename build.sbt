import scalapb.compiler.Version._
import sbtrelease.ReleaseStateTransformations._
import sbtcrossproject.CrossPlugin.autoImport.crossProject

val Scala212 = "2.12.20"
val argonautVersion = settingKey[String]("")
val scalapbJsonCommonVersion = settingKey[String]("")

val tagName = Def.setting {
  s"v${if (releaseUseGlobalVersion.value) (ThisBuild / version).value else version.value}"
}

val tagOrHash = Def.setting {
  if (isSnapshot.value) sys.process.Process("git rev-parse HEAD").lineStream_!.head
  else tagName.value
}

val unusedWarnings = Seq("-Ywarn-unused:imports")

lazy val macros = project
  .in(file("macros"))
  .settings(
    commonSettings,
    name := UpdateReadme.scalapbArgonautMacrosName,
    libraryDependencies ++= Seq(
      "io.github.scalapb-json" %%% "scalapb-json-macros" % scalapbJsonCommonVersion.value,
    ),
  )
  .dependsOn(
    scalapbArgonautJVM,
  )

lazy val tests = crossProject(JVMPlatform)
  .in(file("tests"))
  .settings(
    commonSettings,
    noPublish,
    libraryDependencies += "org.scalatest" %%% "scalatest" % "3.2.19" % "test",
  )
  .configure(_ dependsOn macros)
  .dependsOn(
    scalapbArgonaut % "test->test"
  )

lazy val testsJVM = tests.jvm

val scalapbScala3Sources = Def.setting(
  scalaBinaryVersion.value match {
    case "2.12" =>
      false
    case _ =>
      true
  }
)

val scalapbArgonaut = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .in(file("core"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    commonSettings,
    name := UpdateReadme.scalapbArgonautName,
    (Compile / packageSrc / mappings) ++= (Compile / managedSources).value.map { f =>
      // https://github.com/sbt/sbt-buildinfo/blob/v0.7.0/src/main/scala/sbtbuildinfo/BuildInfoPlugin.scala#L58
      val buildInfoDir = "sbt-buildinfo"
      val path = if (f.getAbsolutePath.contains(buildInfoDir)) {
        (file(buildInfoPackage.value) / f
          .relativeTo((Compile / sourceManaged).value / buildInfoDir)
          .get
          .getPath).getPath
      } else {
        f.relativeTo((Compile / sourceManaged).value).get.getPath
      }
      (f, path)
    },
    buildInfoPackage := "scalapb_argonaut",
    buildInfoObject := "ScalapbArgonautBuildInfo",
    buildInfoKeys := Seq[BuildInfoKey](
      "scalapbVersion" -> scalapbVersion,
      argonautVersion,
      scalapbJsonCommonVersion,
      scalaVersion,
      version
    )
  )
  .jvmSettings(
    (Test / PB.targets) ++= Seq[protocbridge.Target](
      PB.gens.java -> (Test / sourceManaged).value,
      scalapb.gen(
        javaConversions = true,
        scala3Sources = scalapbScala3Sources.value
      ) -> (Test / sourceManaged).value
    ),
    libraryDependencies ++= Seq(
      "com.github.scalaprops" %%% "scalaprops-shapeless" % "0.6.0" % "test",
      "com.google.protobuf" % "protobuf-java-util" % "3.25.8" % "test",
      "com.google.protobuf" % "protobuf-java" % "3.25.8" % "protobuf"
    )
  )
  .jsSettings(
    buildInfoKeys ++= Seq[BuildInfoKey](
      "scalajsVersion" -> scalaJSVersion
    ),
    scalacOptions += {
      val a = (LocalRootProject / baseDirectory).value.toURI.toString
      val g = "https://raw.githubusercontent.com/scalapb-json/scalapb-argonaut/" + tagOrHash.value
      if (scalaBinaryVersion.value == "3") {
        "-scalajs-mapSourceURI:$a->$g/"
      } else {
        "-P:scalajs:mapSourceURI:$a->$g/"
      }
    },
  )
  .platformsSettings(JVMPlatform, JSPlatform)(
    Seq(Compile, Test).map { x =>
      x / unmanagedSourceDirectories += {
        baseDirectory.value.getParentFile / "jvm-js" / "src" / Defaults.nameForSrc(
          x.name
        ) / "scala",
      }
    },
  )
  .platformsSettings(JSPlatform, NativePlatform)(
    (Test / PB.targets) ++= Seq[protocbridge.Target](
      scalapb.gen(
        javaConversions = false,
        scala3Sources = scalapbScala3Sources.value
      ) -> (Test / sourceManaged).value
    )
  )

commonSettings

val noPublish = Seq(
  PgpKeys.publishLocalSigned := {},
  PgpKeys.publishSigned := {},
  publishLocal := {},
  publish := {},
  Compile / publishArtifact := false
)

noPublish

lazy val commonSettings = Def.settings(
  scalapropsCoreSettings,
  (Compile / unmanagedResources) += (LocalRootProject / baseDirectory).value / "LICENSE.txt",
  scalaVersion := Scala212,
  crossScalaVersions := Seq(Scala212, "2.13.16", "3.3.6"),
  scalacOptions ++= {
    if (scalaBinaryVersion.value == "3") {
      Nil
    } else {
      unusedWarnings ++ Seq(
        "-Xsource:3"
      )
    }
  },
  Seq(Compile, Test).flatMap(c => c / console / scalacOptions --= unusedWarnings),
  scalacOptions ++= Seq("-feature", "-deprecation", "-language:existentials"),
  description := "Json/Protobuf convertors for ScalaPB",
  licenses += ("MIT", url("https://opensource.org/licenses/MIT")),
  organization := "io.github.scalapb-json",
  Project.inConfig(Test)(sbtprotoc.ProtocPlugin.protobufConfigSettings),
  Compile / PB.targets := Nil,
  (Test / PB.protoSources) := Seq(baseDirectory.value.getParentFile / "shared/src/test/protobuf"),
  scalapbJsonCommonVersion := "0.10.0",
  argonautVersion := "6.3.10",
  libraryDependencies ++= Seq(
    "com.github.scalaprops" %%% "scalaprops" % "0.10.0" % "test",
    "io.github.scalapb-json" %%% "scalapb-json-common" % scalapbJsonCommonVersion.value,
    "com.thesamet.scalapb" %%% "scalapb-runtime" % scalapbVersion % "protobuf,test",
    "io.argonaut" %%% "argonaut" % argonautVersion.value,
    "com.lihaoyi" %%% "utest" % "0.8.4" % "test"
  ),
  testFrameworks += new TestFramework("utest.runner.Framework"),
  (Global / pomExtra) := {
    <url>https://github.com/scalapb-json/scalapb-argonaut</url>
      <scm>
        <connection>scm:git:github.com/scalapb-json/scalapb-argonaut.git</connection>
        <developerConnection>scm:git:git@github.com:scalapb-json/scalapb-argonaut.git</developerConnection>
        <url>github.com/scalapb-json/scalapb-argonaut.git</url>
        <tag>{tagOrHash.value}</tag>
      </scm>
      <developers>
        <developer>
          <id>xuwei-k</id>
          <name>Kenji Yoshida</name>
          <url>https://github.com/xuwei-k</url>
        </developer>
      </developers>
  },
  publishTo := sonatypePublishToBundle.value,
  (Compile / doc / scalacOptions) ++= {
    val t = tagOrHash.value
    Seq(
      "-sourcepath",
      (LocalRootProject / baseDirectory).value.getAbsolutePath,
      "-doc-source-url",
      s"https://github.com/scalapb-json/scalapb-argonaut/tree/${t}€{FILE_PATH}.scala"
    )
  },
  compileOrder := {
    if (scalaBinaryVersion.value == "3") {
      // https://github.com/scala/scala3/issues/10956
      // https://github.com/scala/scala3/issues/6138
      CompileOrder.JavaThenScala
    } else {
      compileOrder.value
    }
  },
  ReleasePlugin.extraReleaseCommands,
  commands += Command.command("updateReadme")(UpdateReadme.updateReadmeTask),
  releaseCrossBuild := true,
  releaseTagName := tagName.value,
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    UpdateReadme.updateReadmeProcess,
    tagRelease,
    ReleaseStep(
      action = { state =>
        val extracted = Project extract state
        extracted
          .runAggregated(extracted.get(thisProjectRef) / (Global / PgpKeys.publishSigned), state)
      },
      enableCrossBuild = true
    ),
    releaseStepCommand("sonatypeBundleRelease"),
    setNextVersion,
    commitNextVersion,
    UpdateReadme.updateReadmeProcess,
    pushChanges
  )
)

val scalapbArgonautJVM = scalapbArgonaut.jvm
val scalapbArgonautJS = scalapbArgonaut.js

commonSettings
publishArtifact := false
publish := {}
publishLocal := {}
PgpKeys.publishSigned := {}
PgpKeys.publishLocalSigned := {}

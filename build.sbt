import play.sbt.routes.RoutesKeys

import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "3.6.3"

lazy val microservice = Project("transit-movements-push-notifications", file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .settings(
    PlayKeys.playDefaultPort := 9508,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
    // suppress warnings in generated routes files
    scalacOptions += "-Wconf:src=routes/.*:s",
    scalacOptions += "-Wconf:msg=Flag.*repeatedly:s",
    // Import models by default in route files
    RoutesKeys.routesImport ++= Seq(
      "uk.gov.hmrc.transitmovementspushnotifications.models._",
      "uk.gov.hmrc.transitmovementspushnotifications.models.Bindings._"
    )
  )
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(scoverageSettings)
  .settings(inThisBuild(buildSettings))

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(DefaultBuildSettings.itSettings())
  .settings(
    libraryDependencies ++= AppDependencies.test,
    scalacOptions += "-Wconf:msg=Flag.*repeatedly:s"
  )
  .settings(CodeCoverageSettings.settings: _*)

// Scoverage exclusions and minimums
lazy val scoverageSettings = Def.settings(
  Test / parallelExecution               := false,
  ScoverageKeys.coverageMinimumStmtTotal := 90,
  ScoverageKeys.coverageFailOnMinimum    := true,
  ScoverageKeys.coverageHighlighting     := true,
  ScoverageKeys.coverageExcludedPackages := Seq(
    "<empty>",
    "Reverse.*",
    ".*(config|views.*)",
    ".*(BuildInfo|Routes).*"
  ).mkString(";"),
  ScoverageKeys.coverageExcludedFiles := Seq(
    "<empty>",
    "Reverse.*",
    ".*repositories.*",
    ".*documentation.*",
    ".*BuildInfo.*",
    ".*javascript.*",
    ".*Routes.*",
    ".*GuiceInjector",
    ".*Test.*",
    ".*models.*"
  ).mkString(";")
)

// Settings for the whole build
lazy val buildSettings = Def.settings(
  scalafmtOnCompile := true
)

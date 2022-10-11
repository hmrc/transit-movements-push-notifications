import play.sbt.routes.RoutesKeys
import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

lazy val microservice = Project("transit-movements-push-notifications", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(
    majorVersion := 0,
    scalaVersion := "2.13.8",
    PlayKeys.playDefaultPort := 9508,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
    // suppress warnings in generated routes files
    scalacOptions += "-Wconf:src=routes/.*:s",
    // Import models by default in route files
    RoutesKeys.routesImport ++= Seq(
      "uk.gov.hmrc.transitmovementspushnotifications.models._"
    )
  )
  .settings(publishingSettings: _*)
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(CodeCoverageSettings.settings: _*)
  .settings(inThisBuild(buildSettings))

lazy val itSettings = Seq(
  // Must fork so that config system properties are set
  fork := true,
  unmanagedResourceDirectories += (baseDirectory.value / "it" / "resources"),
  javaOptions ++= Seq(
    "-Dlogger.resource=it.logback.xml"
  )
)

// Settings for the whole build
lazy val buildSettings = Def.settings(
  scalafmtOnCompile := true
)

import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  private val bootstrapPlayVersion = "7.2.0"
  private val catsVersion          = "2.6.1"

  val compile = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28" % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % "0.73.0",
    "io.lemonlabs"      %% "scala-uri"                 % "4.0.2",
    "org.typelevel"     %% "cats-core"                 % catsVersion
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"  % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28" % "0.73.0",
    "org.mockito"             % "mockito-core"            % "4.5.1",
    "org.scalatest"          %% "scalatest"               % "3.2.12",
    "org.typelevel"          %% "cats-core"               % catsVersion,
    "com.typesafe.play"      %% "play-test"               % PlayVersion.current,
    "com.typesafe.akka"      %% "akka-testkit"            % PlayVersion.akkaVersion,
    "org.mockito"            %% "mockito-scala-scalatest" % "1.17.5",
    "org.pegdown"             % "pegdown"                 % "1.6.0",
    "org.scalatestplus.play" %% "scalatestplus-play"      % "5.1.0",
    "org.scalatestplus"      %% "mockito-3-2"             % "3.1.2.0",
    "org.scalacheck"         %% "scalacheck"              % "1.16.0",
    "com.github.tomakehurst"  % "wiremock-standalone"     % "2.27.2",
    "org.typelevel"          %% "discipline-scalatest"    % "2.1.5",
    "com.vladsch.flexmark"    % "flexmark-all"            % "0.62.2"
  ).map(_ % "test, it")
}

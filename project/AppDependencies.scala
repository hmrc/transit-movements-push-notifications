import sbt._

object AppDependencies {

  private val bootstrapPlayVersion = "8.4.0"
  private val catsVersion          = "2.6.1"

  val compile = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30"    % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"           % "1.4.0",
    "io.lemonlabs"      %% "scala-uri"                    % "4.0.2",
    "org.typelevel"     %% "cats-core"                    % catsVersion,
    "uk.gov.hmrc"       %% "internal-auth-client-play-30" % "1.8.0"
  )

  val test = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % "1.4.0",
    "org.typelevel"     %% "cats-core"               % catsVersion,
    "org.apache.pekko"  %% "pekko-testkit"           % "1.0.2",
    "org.mockito"       %% "mockito-scala-scalatest" % "1.17.14",
    "org.pegdown"        % "pegdown"                 % "1.6.0",
    "org.scalatestplus" %% "mockito-3-2"             % "3.1.2.0",
    "org.scalacheck"    %% "scalacheck"              % "1.16.0",
    "org.typelevel"     %% "discipline-scalatest"    % "2.1.5"
  ).map(_ % Test)
}

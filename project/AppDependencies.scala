import sbt._

object AppDependencies {

  private val bootstrapPlayVersion = "9.7.0"
  private val catsVersion          = "2.6.1"
  private val mongoPlay            = "2.2.0"

  val compile = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30"    % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"           % mongoPlay,
    "io.lemonlabs"      %% "scala-uri"                    % "4.0.2",
    "org.typelevel"     %% "cats-core"                    % catsVersion,
    "uk.gov.hmrc"       %% "internal-auth-client-play-30" % "3.0.0"
  )

  val test = Seq(
    "org.apache.pekko"  %% "pekko-testkit"           % "1.0.3",
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % mongoPlay,
    "org.typelevel"     %% "cats-core"               % catsVersion,
    "org.apache.pekko"  %% "pekko-testkit"           % "1.0.2",
    "org.pegdown"        % "pegdown"                 % "1.6.0",
    "org.scalatestplus" %% "mockito-5-12"            % "3.2.19.0",
    "org.scalacheck"    %% "scalacheck"              % "1.16.0",
    "org.typelevel"     %% "discipline-scalatest"    % "2.1.5"
  ).map(_ % Test)
}

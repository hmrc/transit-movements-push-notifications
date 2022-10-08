import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  private val bootstrapPlayVersion = "6.2.0"
  private val catsVersion          = "2.6.1"

  val compile = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28" % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % "0.73.0",
    "io.lemonlabs"      %% "scala-uri"                 % "4.0.2",
    "org.typelevel"     %% "cats-core"                 % catsVersion
  )

  val test = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-28"  % bootstrapPlayVersion % "test, it",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-28" % "0.73.0"             % Test
  )
}

package uk.gov.hmrc.transitmovementspushnotifications.repositories

import cats.data.EitherT
import akka.pattern.retry
import com.google.inject.ImplementedBy
import org.mongodb.scala.model.IndexModel
import org.mongodb.scala.model.IndexOptions
import org.mongodb.scala.model.Indexes
import play.api.Logging
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.transitmovementspushnotifications.config.AppConfig
import uk.gov.hmrc.transitmovementspushnotifications.models.MovementBoxAssociation
import uk.gov.hmrc.transitmovementspushnotifications.models.formats.CommonFormats
import uk.gov.hmrc.transitmovementspushnotifications.models.formats.MongoFormats
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.MongoError
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.MongoError.InsertNotAcknowledged
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.MongoError.UnexpectedError

import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.NonFatal

@ImplementedBy(classOf[MovementBoxAssociationRepositoryImpl])
trait MovementBoxAssociationRepository {
  def insert(movementBox: MovementBoxAssociation): EitherT[Future, MongoError, Unit]
}

@Singleton
class MovementBoxAssociationRepositoryImpl(
  appConfig: AppConfig,
  mongoComponent: MongoComponent,
  clock: Clock
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[MovementBoxAssociation](
      mongoComponent = mongoComponent,
      collectionName = "movement_box_association",
      domainFormat = MongoFormats.movementBoxAssociationFormat,
      indexes = Seq(
        IndexModel(Indexes.ascending("updated"), IndexOptions().expireAfter(appConfig.documentTtl, TimeUnit.SECONDS))
      ),
      extraCodecs = Seq(
        Codecs.playFormatCodec(MongoFormats.movementBoxAssociationFormat),
        Codecs.playFormatCodec(MongoFormats.offsetDateTimeFormat)
      )
    )
    with MovementBoxAssociationRepository
    with Logging
    with CommonFormats {

  private def mongoRetry[A](func: Future[Either[MongoError, A]]): EitherT[Future, MongoError, A] =
    EitherT {
      retry(
        attempts = appConfig.mongoRetryAttempts,
        attempt = () => func
      )
    }

  override def insert(movementBox: MovementBoxAssociation): EitherT[Future, MongoError, Unit] =
    mongoRetry(Try(collection.insertOne(movementBox)) match {
      case Success(obs) =>
        obs.toFuture().map {
          result =>
            if (result.wasAcknowledged()) {
              Right(())
            } else {
              Left(InsertNotAcknowledged(s"Insert failed for movement ${movementBox.movementId}"))
            }
        }
      case Failure(NonFatal(ex)) =>
        Future.successful(Left(UnexpectedError(Some(ex))))
    })

}

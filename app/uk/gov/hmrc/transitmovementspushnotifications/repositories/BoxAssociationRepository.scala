/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.transitmovementspushnotifications.repositories

import akka.pattern.retry
import cats.data.EitherT
import com.google.inject.ImplementedBy
import com.mongodb.client.model.Filters.{eq => mEq}
import com.mongodb.client.model.Updates.{set => mSet}
import org.mongodb.scala.model._
import play.api.Logging
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json._
import uk.gov.hmrc.transitmovementspushnotifications.config.AppConfig
import uk.gov.hmrc.transitmovementspushnotifications.models._
import uk.gov.hmrc.transitmovementspushnotifications.models.formats._
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.MongoError
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.MongoError._

import java.time._
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent._
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.NonFatal

@ImplementedBy(classOf[BoxAssociationRepositoryImpl])
trait BoxAssociationRepository {
  def insert(boxAssociation: BoxAssociation): EitherT[Future, MongoError, Unit]
  def getBoxId(movementId: MovementId): EitherT[Future, MongoError, BoxId]
}

@Singleton
class BoxAssociationRepositoryImpl @Inject() (
  appConfig: AppConfig,
  mongoComponent: MongoComponent,
  clock: Clock
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[BoxAssociation](
      mongoComponent = mongoComponent,
      collectionName = "box_association",
      domainFormat = MongoFormats.boxAssociationFormat,
      indexes = Seq(
        IndexModel(Indexes.ascending("updated"), IndexOptions().expireAfter(appConfig.documentTtl, TimeUnit.SECONDS))
      ),
      extraCodecs = Seq(
        Codecs.playFormatCodec(MongoFormats.boxAssociationFormat),
        Codecs.playFormatCodec(MongoFormats.offsetDateTimeFormat),
        Codecs.playFormatCodec(MongoFormats.boxIdFormat),
        Codecs.playFormatCodec(MongoFormats.movementIdFormat)
      )
    )
    with BoxAssociationRepository
    with Logging
    with CommonFormats {

  private def mongoRetry[A](func: Future[Either[MongoError, A]]): EitherT[Future, MongoError, A] =
    EitherT {
      retry(
        attempts = appConfig.mongoRetryAttempts,
        attempt = () => func
      )
    }

  override def insert(boxAssociation: BoxAssociation): EitherT[Future, MongoError, Unit] =
    mongoRetry(Try(collection.insertOne(boxAssociation)) match {
      case Success(obs) =>
        obs
          .toFuture()
          .map {
            result =>
              if (result.wasAcknowledged()) {
                Right(())
              } else {
                Left(InsertNotAcknowledged(s"Insert failed for movement ${boxAssociation._id.value}"))
              }
          }
          .recover {
            case NonFatal(ex) => Left(UnexpectedError(Some(ex)))
          }
      case Failure(ex) =>
        Future.successful(Left(UnexpectedError(Some(ex))))
    })

  override def getBoxId(movementId: MovementId): EitherT[Future, MongoError, BoxId] = {
    val setUpdated = mSet("updated", OffsetDateTime.ofInstant(clock.instant, ZoneOffset.UTC))
    mongoRetry(Try(collection.findOneAndUpdate(mEq(movementId), setUpdated).headOption()) match {
      case Success(fOptBox) =>
        fOptBox.map(
          optBox =>
            optBox match {
              case Some(box) => Right(box.boxId)
              case None      => Left(DocumentNotFound(s"Could not find BoxAssociation with id: ${movementId.value}"))
            }
        )
      case Failure(ex) => Future.successful(Left(UnexpectedError(Some(ex))))
    })
  }

}

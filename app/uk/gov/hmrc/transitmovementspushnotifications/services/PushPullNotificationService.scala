/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.transitmovementspushnotifications.services

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import com.google.inject.ImplementedBy
import play.api.Logging
import play.api.http.Status._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.transitmovementspushnotifications.config.AppConfig
import uk.gov.hmrc.transitmovementspushnotifications.connectors.PushPullNotificationConnector
import uk.gov.hmrc.transitmovementspushnotifications.controllers.errors.ConvertError
import uk.gov.hmrc.transitmovementspushnotifications.models._
import uk.gov.hmrc.transitmovementspushnotifications.models.request.BoxAssociationRequest
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.PushPullNotificationError
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.PushPullNotificationError._

import javax.inject._
import scala.concurrent._
import scala.util.control.NonFatal

@ImplementedBy(classOf[PushPullNotificationServiceImpl])
trait PushPullNotificationService {

  def getBoxId(
    boxAssociationRequest: BoxAssociationRequest
  )(implicit ec: ExecutionContext, hc: HeaderCarrier): EitherT[Future, PushPullNotificationError, BoxId]

  def sendPushNotification(
    boxAssociation: BoxAssociation,
    contentLength: Option[String],
    messageId: MessageId,
    body: Option[Source[ByteString, _]],
    notificationType: NotificationType
  )(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier,
    mat: Materializer
  ): EitherT[Future, PushPullNotificationError, Unit]

}

@Singleton
class PushPullNotificationServiceImpl @Inject() (pushPullNotificationConnector: PushPullNotificationConnector, appConfig: AppConfig)
    extends PushPullNotificationService
    with ConvertError
    with Logging {

  override def getBoxId(
    boxAssociationRequest: BoxAssociationRequest
  )(implicit ec: ExecutionContext, hc: HeaderCarrier): EitherT[Future, PushPullNotificationError, BoxId] =
    boxAssociationRequest match {
      case BoxAssociationRequest(clientId, _, Some(boxId)) => checkBoxIdExists(clientId, boxId)
      case BoxAssociationRequest(clientId, _, None)        => getDefaultBoxId(clientId)
    }

  override def sendPushNotification(
    boxAssociation: BoxAssociation,
    contentLength: Option[String],
    messageId: MessageId,
    body: Option[Source[ByteString, _]],
    notificationType: NotificationType
  )(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier,
    mat: Materializer
  ): EitherT[Future, PushPullNotificationError, Unit] = {

    lazy val uri = buildUriAsString(boxAssociation._id, messageId, boxAssociation.movementType)
    EitherT(
      (body match {
        case None => pushPullNotificationConnector.postNotification(boxAssociation.boxId, MessageNotification(uri, notificationType, None, None))
        case Some(body) =>
          contentLength
            .flatMap(_.toIntOption)
            .filter(_ <= appConfig.maxPushPullPayloadSize)
            .map {
              _ =>
                body
                  .reduce(_ ++ _)
                  .map(_.utf8String)
                  .runWith(Sink.headOption)
            }
            .getOrElse(Future.successful(None))
            .flatMap {
              bodyOpt =>
                if (NotificationType.SUBMISSION_NOTIFICATION == notificationType)
                  pushPullNotificationConnector.postNotification(boxAssociation.boxId, MessageNotification(uri, notificationType, None, bodyOpt))
                else {
                  pushPullNotificationConnector.postNotification(boxAssociation.boxId, MessageNotification(uri, notificationType, bodyOpt, None))
                }
            }
      }).map {
        case Right(_) => Right(())
        case Left(error) =>
          error.statusCode match {
            case NOT_FOUND =>
              logger.warn(
                s"Attempted to send notification for movement '${boxAssociation._id.value}' to box ID '${boxAssociation.boxId.value}', but the box no longer exists."
              )
              Left(BoxNotFound(boxAssociation.boxId))
            case _ => Left(UnexpectedError(Some(error)))

          }
      }
    )
  }

  private def getDefaultBoxId(clientId: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): EitherT[Future, PushPullNotificationError, BoxId] =
    EitherT(
      pushPullNotificationConnector
        .getBox(clientId)
        .map {
          boxResponse => Right(boxResponse.boxId)
        }
        .recover {
          case UpstreamErrorResponse(_, NOT_FOUND, _, _) =>
            logger.warn(s"Client ID '$clientId' did not return a default box.")
            Left(PushPullNotificationError.DefaultBoxNotFound)
          case NonFatal(e) => Left(UnexpectedError(thr = Some(e)))
        }
    )

  private def checkBoxIdExists(clientId: String, boxId: BoxId)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): EitherT[Future, PushPullNotificationError, BoxId] =
    EitherT(
      pushPullNotificationConnector.getAllBoxes
        .map {
          boxList =>
            // TODO: We should restrict this to boxes associated to the appropriate client ID
            if (boxList.exists(_.boxId == boxId)) Right(boxId)
            else {
              logger.warn(s"Client ID '$clientId' requested box ID '$boxId', but it did not exist.")
              Left(BoxNotFound(boxId))
            }
        }
        .recover {
          case NonFatal(e) =>
            Left(UnexpectedError(thr = Some(e)))
        }
    )

  private def buildUriAsString(movementId: MovementId, messageId: MessageId, movementType: MovementType): String =
    s"/customs/transits/movements/${movementType.urlFragment}/${movementId.value}/messages/${messageId.value}"

}

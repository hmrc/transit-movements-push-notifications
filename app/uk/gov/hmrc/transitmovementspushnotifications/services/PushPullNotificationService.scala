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

package uk.gov.hmrc.transitmovementspushnotifications.services

import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import com.google.inject.ImplementedBy
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.FORBIDDEN
import play.api.http.Status.NOT_FOUND
import play.api.http.Status.REQUEST_ENTITY_TOO_LARGE
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.transitmovementspushnotifications.config.AppConfig
import uk.gov.hmrc.transitmovementspushnotifications.connectors.PushPullNotificationConnector
import uk.gov.hmrc.transitmovementspushnotifications.controllers.errors.ConvertError
import uk.gov.hmrc.transitmovementspushnotifications.models.BoxId
import uk.gov.hmrc.transitmovementspushnotifications.models.MessageId
import uk.gov.hmrc.transitmovementspushnotifications.models.MessageNotification
import uk.gov.hmrc.transitmovementspushnotifications.models.MovementId
import uk.gov.hmrc.transitmovementspushnotifications.models.request.BoxAssociationRequest
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.PushPullNotificationError
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.PushPullNotificationError.BadRequest
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.PushPullNotificationError.BoxNotFound
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.PushPullNotificationError.Forbidden
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.PushPullNotificationError.RequestTooLarge
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.PushPullNotificationError.UnexpectedError

import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal

@ImplementedBy(classOf[PushPullNotificationServiceImpl])
trait PushPullNotificationService {

  def getBoxId(
    boxAssociationRequest: BoxAssociationRequest
  )(implicit ec: ExecutionContext, hc: HeaderCarrier): EitherT[Future, PushPullNotificationError, BoxId]

  def sendPushNotification(boxId: Option[BoxId], contentLength: Option[String], movementId: MovementId, messageId: MessageId, body: Source[ByteString, _])(
    implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): EitherT[Future, PushPullNotificationError, Unit]

}

@Singleton
class PushPullNotificationServiceImpl @Inject() (pushPullNotificationConnector: PushPullNotificationConnector, appConfig: AppConfig)
    extends PushPullNotificationService
    with ConvertError {

  override def getBoxId(
    boxAssociationRequest: BoxAssociationRequest
  )(implicit ec: ExecutionContext, hc: HeaderCarrier): EitherT[Future, PushPullNotificationError, BoxId] =
    boxAssociationRequest match {
      case BoxAssociationRequest(_, Some(boxId)) => checkBoxIdExists(boxId)
      case BoxAssociationRequest(clientId, None) => getDefaultBoxId(clientId)
    }

  private def getDefaultBoxId(clientId: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): EitherT[Future, PushPullNotificationError, BoxId] =
    EitherT(
      pushPullNotificationConnector
        .getBox(clientId)
        .map {
          boxResponse => Right(boxResponse.boxId)
        }
        .recover {
          case NonFatal(e) =>
            Left(PushPullNotificationError.UnexpectedError(thr = Some(e)))
        }
    )

  private def checkBoxIdExists(boxId: BoxId)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): EitherT[Future, PushPullNotificationError, BoxId] =
    EitherT(
      pushPullNotificationConnector.getAllBoxes
        .map {
          boxList =>
            if (boxList.exists(_.boxId == boxId)) Right(boxId)
            else Left(PushPullNotificationError.InvalidBoxId(s"Box id provided does not exist: $boxId"))
        }
        .recover {
          case NonFatal(e) =>
            Left(PushPullNotificationError.UnexpectedError(thr = Some(e)))
        }
    )

  override def sendPushNotification(
    boxId: Option[BoxId],
    contentLength: Option[String],
    movementId: MovementId,
    messageId: MessageId,
    body: Source[ByteString, _]
  )(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): EitherT[Future, PushPullNotificationError, Unit] = {

    val messageNotification = getMessageNotification(
      contentLength: Option[String],
      movementId: MovementId,
      messageId: MessageId,
      body: Source[ByteString, _]
    )

    EitherT(
      pushPullNotificationConnector
        .postNotification(boxId.get, messageNotification, body)
        .map {
          case Right(_) => Right((): Unit)
          case Left(error) =>
            error.statusCode match {
              case BAD_REQUEST              => Left(BadRequest("Box ID is not a UUID / Request body does not match the Content-Type header"))
              case FORBIDDEN                => Left(Forbidden("Access denied, service is not allowlisted"))
              case NOT_FOUND                => Left(BoxNotFound("Box does not exist"))
              case REQUEST_ENTITY_TOO_LARGE => Left(RequestTooLarge("Request is too large"))
              case ex @ _                   => Left(UnexpectedError(Some(new Exception(s"Unexpected error: $ex"))))
            }
        }
    )
  }

  private def getMessageNotification(
    contentLength: Option[String],
    movementId: MovementId,
    messageId: MessageId,
    body: Source[ByteString, _]
  ): MessageNotification = {

    val payloadExceedsLimit: Boolean = {
      val maybeSize: Option[Int] = contentLength.flatMap(
        str => str.toIntOption
      )
      if (maybeSize.getOrElse(0) > appConfig.maxPushPullPayloadSize) true else false
    }

    val uri =
      if (payloadExceedsLimit)
        Some(
          new URI(
            s"/customs/transits/movements/departures/${movementId.value}/messages/${messageId.value}"
          )
        )
      else None

    MessageNotification(
      uri = uri,
      messageBody = if (payloadExceedsLimit) None else Some(body)
    )
  }

}

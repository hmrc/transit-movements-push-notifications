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

import akka.http.javadsl.model.headers.ContentLength
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import cats.data.OptionT
import com.google.inject.ImplementedBy
import play.api.http.Status._
import play.api.libs.json.JsString
import play.api.libs.json.Json
import play.api.libs.json.Writes
import play.api.mvc.Result
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.transitmovementspushnotifications.config.AppConfig
import uk.gov.hmrc.transitmovementspushnotifications.connectors.PushPullNotificationConnector
import uk.gov.hmrc.transitmovementspushnotifications.controllers.errors.ConvertError
import uk.gov.hmrc.transitmovementspushnotifications.models._
import uk.gov.hmrc.transitmovementspushnotifications.models.request.BoxAssociationRequest
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.PushPullNotificationError
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.PushPullNotificationError._

import java.net.URI
import java.nio.charset.StandardCharsets
import javax.inject._
import scala.concurrent.Future
import scala.concurrent._
import scala.util.Failure
import scala.util.Success
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
    hc: HeaderCarrier,
    mat: Materializer
  ): EitherT[Future, PushPullNotificationError, Unit] = {

    val result: OptionT[Future, Future[Either[PushPullNotificationError, Unit]]] = for {
      id <- OptionT.fromOption[Future](boxId)
      uri = buildUriAsStream(movementId, messageId)
      payload <- determinePayload(contentLength, body)
      messageNotification = MessageNotification(uri, Some(payload))
      post                = pushPullNotificationConnector.postNotification(id, messageNotification)
    } yield post.map {
      case Right(_) => Right((): Unit)
      case Left(error) =>
        error.statusCode match {
          case NOT_FOUND                                          => Left(BoxNotFound("Box does not exist"))
          case BAD_REQUEST | FORBIDDEN | REQUEST_ENTITY_TOO_LARGE => Left(BadRequest("Bad Request"))
          case ex @ _                                             => Left(UnexpectedError(Some(new Exception(s"Unexpected error: $ex"))))
        }
    }
    result // !!! TODO WRONG Return type!!!
  }

  def determinePayload(contentLength: Option[String], body: Source[ByteString, _])(implicit ec: ExecutionContext, mat: Materializer): OptionT[Future, String] =
    if (payloadExceedsLimit(contentLength)) {
      val noPayload: OptionT[Future, String] = OptionT.none
      noPayload
    } else {
      val payload = OptionT.liftF(bodyToString(body))
      payload
    }

  def payloadExceedsLimit(contentLength: Option[String]): Boolean = {
    val maybeSize: Option[Int] = contentLength.flatMap(
      str => str.toIntOption
    )
    if (maybeSize.getOrElse(0) > appConfig.maxPushPullPayloadSize) true else false
  }

  def buildUriAsStream(movementId: MovementId, messageId: MessageId): String =
    (new URI(
      s"/customs/transits/movements/departures/${movementId.value}/messages/${messageId.value}"
    )).toString

  def maybeBody(contentLength: Option[String], body: Source[ByteString, _])(implicit ec: ExecutionContext, mat: Materializer) =
    if (payloadExceedsLimit(contentLength)) Future.successful("") else bodyToString(body)

  def bodyToString(body: Source[ByteString, _])(implicit ec: ExecutionContext, mat: Materializer): Future[String] =
    body
      .reduce(_ ++ _)
      .map(_.utf8String)
      .runWith(Sink.head)

}

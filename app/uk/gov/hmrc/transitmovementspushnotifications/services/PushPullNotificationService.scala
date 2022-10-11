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

import cats.data.EitherT
import com.google.inject.ImplementedBy
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.transitmovementspushnotifications.connectors.PushPullNotificationConnector
import uk.gov.hmrc.transitmovementspushnotifications.controllers.errors.ConvertError
import uk.gov.hmrc.transitmovementspushnotifications.controllers.errors.PresentationError
import uk.gov.hmrc.transitmovementspushnotifications.models.BoxId
import uk.gov.hmrc.transitmovementspushnotifications.models.request.BoxAssociationRequest
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.PushPullNotificationError

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[PushPullNotificationServiceImpl])
trait PushPullNotificationService {

  def getBoxId(requestBody: JsValue)(implicit ec: ExecutionContext, hc: HeaderCarrier): EitherT[Future, PresentationError, BoxId]

}

@Singleton
class PushPullNotificationServiceImpl @Inject() (pushPullNotificationConnector: PushPullNotificationConnector)
    extends PushPullNotificationService
    with ConvertError {

  override def getBoxId(body: JsValue)(implicit ec: ExecutionContext, hc: HeaderCarrier): EitherT[Future, PresentationError, BoxId] =
    body
      .validate[BoxAssociationRequest] match {
      case JsSuccess(BoxAssociationRequest(_, Some(boxId)), _) => checkBoxIdExists(boxId).asPresentation
      case JsSuccess(BoxAssociationRequest(clientId, None), _) => getDefaultBoxId(clientId).asPresentation
      case _                                                   => EitherT.leftT[Future, BoxId](PresentationError.badRequestError("Expected clientId to be present in the body"))
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
}

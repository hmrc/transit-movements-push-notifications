package uk.gov.hmrc.transitmovementspushnotifications.models

import play.api.libs.json.Json

case class MessageNotification(
  messageUri: String,
  messageBody: Option[String]
)

object MessageNotification {
  implicit val messageNotificationWrites = Json.writes[MessageNotification]
}

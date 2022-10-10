package uk.gov.hmrc.transitmovementspushnotifications.models

import play.api.libs.json.Json

case class Box(clientId: String, boxId: Option[String])

object Box {
  implicit val boxFormat = Json.format[Box]
}

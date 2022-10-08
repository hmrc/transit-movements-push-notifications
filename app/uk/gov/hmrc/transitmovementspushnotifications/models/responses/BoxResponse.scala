package uk.gov.hmrc.transitmovementspushnotifications.models.responses

import play.api.libs.json.Json
import uk.gov.hmrc.transitmovementspushnotifications.models.BoxId

case class BoxCreator(clientId: String)

object BoxCreator {
  implicit val boxCreatorFormat = Json.format[BoxCreator]
}

case class BoxResponse(boxId: BoxId, boxName: String, boxCreator: BoxCreator)

object BoxResponse {
  implicit val boxResponseFormat = Json.format[BoxResponse]
}

package uk.gov.hmrc.transitmovementspushnotifications.controllers.errors

import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.FORBIDDEN
import play.api.http.Status.GATEWAY_TIMEOUT
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.http.Status.NOT_FOUND
import play.api.libs.json.JsError
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.Reads
import play.api.libs.json.Writes

sealed abstract class ErrorCode(val code: String, val statusCode: Int) extends Product with Serializable

object ErrorCode {
  case object BadRequest          extends ErrorCode("BAD_REQUEST", BAD_REQUEST)
  case object NotFound            extends ErrorCode("NOT_FOUND", NOT_FOUND)
  case object Forbidden           extends ErrorCode("FORBIDDEN", FORBIDDEN)
  case object InternalServerError extends ErrorCode("INTERNAL_SERVER_ERROR", INTERNAL_SERVER_ERROR)
  case object GatewayTimeout      extends ErrorCode("GATEWAY_TIMEOUT", GATEWAY_TIMEOUT)

  lazy val errorCodes: Seq[ErrorCode] = Seq(
    BadRequest,
    NotFound,
    Forbidden,
    InternalServerError,
    GatewayTimeout
  )

  implicit val errorCodeWrites: Writes[ErrorCode] = Writes {
    errorCode => JsString(errorCode.code)
  }

  implicit val errorCodeReads: Reads[ErrorCode] = Reads {
    case JsString(errorCode) => errorCodes.find(_.code == errorCode).map(JsSuccess(_)).getOrElse(JsError())
    case _                   => JsError()
  }
}
package uk.gov.hmrc.transitmovementspushnotifications.services.errors

sealed abstract class PushPullNotificationError

object PushPullNotificationError {
  case class UnexpectedError(thr: Option[Throwable] = None) extends PushPullNotificationError
  case class InvalidBoxId(message: String)                  extends PushPullNotificationError
}

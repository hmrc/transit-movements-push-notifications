package uk.gov.hmrc.transitmovementspushnotifications.controllers

import cats.data.EitherT
import org.mockito.Mockito.reset
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import play.api.http.HeaderNames
import play.api.test.FakeHeaders
import play.api.test.Helpers.stubControllerComponents
import uk.gov.hmrc.transitmovementspushnotifications.base.SpecBase
import uk.gov.hmrc.transitmovementspushnotifications.repositories.MovementBoxAssociationRepository
import uk.gov.hmrc.transitmovementspushnotifications.services.MovementBoxAssociationFactory
import uk.gov.hmrc.transitmovementspushnotifications.services.PushPullNotificationService

import java.time.OffsetDateTime

class BoxAssociationControllerSpec extends SpecBase {

  val mockPushPullNotificationService      = mock[PushPullNotificationService]
  val mockMovementBoxAssociationRepository = mock[MovementBoxAssociationRepository]
  val mockMovementBoxAssociationFactory    = mock[MovementBoxAssociationFactory]

  //  lazy val messageDataEither: EitherT[Future, ParseError, MessageData] =
  //    EitherT.rightT(messageData)

  val now = OffsetDateTime.now
  //
  //  lazy val message = arbitraryMessage.arbitrary.sample.get.copy(id = messageId, generated = now, received = now, triggerId = Some(triggerId))
  //
  //  def fakeRequest[A](
  //                      method: String,
  //                      body: NodeSeq,
  //                      headers: FakeHeaders = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> MimeTypes.XML, "X-Message-Type" -> messageType.code))
  //                    ): Request[NodeSeq] =
  //    FakeRequest(
  //      method = method,
  //      uri = routes.MovementsController.updateMovement(movementId, Some(triggerId)).url,
  //      headers = headers,
  //      body = body
  //    )
  //
  //  override def afterEach() {
  //    reset(mockPushPullNotificationService)
  //    reset(mockMovementBoxAssociationRepository)
  //    reset(mockMovementBoxAssociationFactory)
  //    super.afterEach()
  //  }
  //
  //  val controller =
  //    new BoxAssociationController(stubControllerComponents(), mockPushPullNotificationService, mockMovementBoxAssociationRepository, mockMovementBoxAssociationFactory)
  //
  //  "updateMovement" - {
  //
  //    val validXml: NodeSeq =
  //      <CC015C>
  //        <messageSender>ABC123</messageSender>
  //        <preparationDateAndTime>2022-05-25T09:37:04</preparationDateAndTime>
  //      </CC015C>
  //
  //    lazy val messageFactoryEither: EitherT[Future, StreamError, Message] =
  //      EitherT.rightT(message)
  //
  //    "must return OK if XML data extraction is successful" in {
  //
  //      when(mockMessageTypeHeaderExtractor.extract(any[Headers]))
  //        .thenReturn(EitherT.rightT(messageType))
  //
  //      when(mockXmlParsingService.extractMessageData(any[Source[ByteString, _]], any[MessageType]))
  //        .thenReturn(messageDataEither)
  //
  //      when(mockMessageFactory.create(any[MessageType], any[OffsetDateTime], any[Option[MessageId]], any[Source[ByteString, Future[IOResult]]]))
  //        .thenReturn(messageFactoryEither)
  //
  //      when(mockRepository.updateMessages(any[String].asInstanceOf[DepartureId], any[Message], any[Option[MovementReferenceNumber]]))
  //        .thenReturn(EitherT.rightT(()))
  //
  //      val request = fakeRequest(POST, validXml)
  //
  //      val result =
  //        controller.updateMovement(movementId, Some(triggerId))(request)
  //
  //      status(result) mustBe OK
  //      contentAsJson(result) mustBe Json.obj("messageId" -> messageId.value)
}

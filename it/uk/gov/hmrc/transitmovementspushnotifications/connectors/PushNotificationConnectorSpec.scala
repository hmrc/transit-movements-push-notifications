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

package uk.gov.hmrc.transitmovementspushnotifications.connectors

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.Millis
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import play.api.libs.json.Json
import play.api.test.Helpers.REQUEST_ENTITY_TOO_LARGE
import play.api.test.Helpers.BAD_REQUEST
import play.api.test.Helpers.FORBIDDEN
import play.api.test.Helpers.INTERNAL_SERVER_ERROR
import play.api.test.Helpers.NOT_FOUND
import play.api.test.Helpers.OK
import play.api.test.Helpers.await
import play.api.test.Helpers.defaultAwaitTimeout
import play.api.test.Helpers.running
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.transitmovementspushnotifications.config.Constants
import uk.gov.hmrc.transitmovementspushnotifications.generators.ModelGenerators
import uk.gov.hmrc.transitmovementspushnotifications.models.MessageNotification
import uk.gov.hmrc.transitmovementspushnotifications.models.NotificationType
import uk.gov.hmrc.transitmovementspushnotifications.models.responses.BoxResponse
import uk.gov.hmrc.transitmovementspushnotifications.utils.GuiceWiremockSuite

import scala.concurrent.ExecutionContext.Implicits.global

class PushNotificationConnectorSpec extends AnyFreeSpec with Matchers with ScalaFutures with GuiceWiremockSuite with ModelGenerators with OptionValues {
  override protected def portConfigKey: Seq[String] = Seq("microservice.services.push-pull-notifications-api.port")

  implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(2, Seconds), interval = Span(5, Millis))

  "PushNotificationConnector" - {

    "getBox" - {

      val boxId    = arbitraryBoxId.arbitrary.sample.get
      val clientId = "Client_123"

      "should return a BoxResponse when the pushPullNotification API returns 200 and valid JSON" in {
        server.stubFor {
          get(urlPathEqualTo("/box")).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(s"""
                {
                  "boxId": "${boxId.value}",
                  "boxName":"${Constants.BoxName}",
                  "boxCreator":{
                      "clientId": "$clientId"
                  }
                }
              """)
          )
        }

        val app = applicationBuilder.build()

        running(app) {
          val connector = app.injector.instanceOf[PushPullNotificationConnector]
          whenReady(connector.getBox(clientId)) {
            result =>
              result mustEqual BoxResponse(boxId)
          }
        }

      }

      "should return failed future when the pushPullNotification API returns 404" in {
        server.stubFor {
          get(urlPathEqualTo("/box")).willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
          )
        }

        val app = applicationBuilder.build()

        running(app) {
          val connector = app.injector.instanceOf[PushPullNotificationConnector]
          val result    = connector.getBox(clientId)

          await(
            result
              .map {
                _ => fail("This should not succeed")
              }
              .recover {
                case UpstreamErrorResponse(_, NOT_FOUND, _, _) =>
              }
          )
        }
      }

      "should return failed future when the pushPullNotification API returns 500" in {
        server.stubFor {
          get(urlPathEqualTo("/box")).willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
        }

        val app = applicationBuilder.build()

        running(app) {
          val connector = app.injector.instanceOf[PushPullNotificationConnector]
          val result    = connector.getBox(clientId)

          await(
            result
              .map {
                _ => fail("This should not succeed")
              }
              .recover {
                case UpstreamErrorResponse(_, INTERNAL_SERVER_ERROR, _, _) =>
              }
          )
        }
      }
    }

    "getAllBoxes" - {
      lazy val boxIdList = Gen.listOfN(3, arbitrary[BoxResponse]).sample.get

      "should return list of box id when the pushPullNotification API returns 200 and valid JSON" in {
        server.stubFor {
          get(urlPathEqualTo("/box")).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(Json.toJson(boxIdList).toString())
          )
        }

        val app = applicationBuilder.build()

        running(app) {
          val connector = app.injector.instanceOf[PushPullNotificationConnector]
          whenReady(connector.getAllBoxes) {
            result =>
              result mustEqual boxIdList
          }
        }

      }

      "should return failed future when the pushPullNotification API returns 404" in {
        server.stubFor {
          get(urlPathEqualTo("/box")).willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
          )
        }

        val app = applicationBuilder.build()

        running(app) {
          val connector = app.injector.instanceOf[PushPullNotificationConnector]
          val result    = connector.getAllBoxes

          await(
            result
              .map {
                _ => fail("This should not succeed")
              }
              .recover {
                case UpstreamErrorResponse(_, NOT_FOUND, _, _) =>
              }
          )
        }
      }

      "should return failed future when the pushPullNotification API returns 500" in {
        server.stubFor {
          get(urlPathEqualTo("/box")).willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
        }

        val app = applicationBuilder.build()

        running(app) {
          val connector = app.injector.instanceOf[PushPullNotificationConnector]
          val result    = connector.getAllBoxes

          await(
            result
              .map {
                _ => fail("This should not succeed")
              }
              .recover {
                case UpstreamErrorResponse(_, INTERNAL_SERVER_ERROR, _, _) =>
              }
          )
        }
      }
    }

    "postNotification" - {

      val boxId        = arbitraryBoxId.arbitrary.sample.value
      val validPayload = """{"message":"<CC007C>data</CC007C>"}"""

      val messageNotificationWithBody = MessageNotification(
        messageUri = "/customs/transits/movements/departures/movement-id-1/messages/message-id-1",
        notificationType = NotificationType.MESSAGE_RECEIVED,
        messageBody = Some(validPayload),
        None
      )

      val messageNotificationWithoutBody = MessageNotification(
        messageUri = "/customs/transits/movements/departures/movement-id-1/messages/message-id-1",
        notificationType = NotificationType.SUBMISSION_NOTIFICATION,
        messageBody = None,
        response = None
      )

      "when called with a valid message notification with a body and box id that is in the database" - {
        "should return Unit () when the post is successful" in {
          server.stubFor {
            post(urlPathEqualTo(s"/box/${boxId.value}/notifications")).willReturn(
              aResponse()
                .withStatus(OK)
            )
          }

          val app = applicationBuilder.build()
          running(app) {
            val connector = app.injector.instanceOf[PushPullNotificationConnector]
            whenReady(connector.postNotification(boxId, messageNotificationWithBody)) {
              _ mustEqual Right(())
            }
          }
        }
      }

      "when called with a valid message notification with no body and box id that is in the database" - {
        "should return Unit () when the post is successful" in {
          server.stubFor {
            post(urlPathEqualTo(s"/box/${boxId.value}/notifications")).willReturn(
              aResponse()
                .withStatus(OK)
            )
          }

          val app = applicationBuilder.build()
          running(app) {
            val connector = app.injector.instanceOf[PushPullNotificationConnector]
            whenReady(connector.postNotification(boxId, messageNotificationWithoutBody)) {
              _ mustEqual Right(())
            }
          }
        }
      }

      for (error <- List(BAD_REQUEST, FORBIDDEN, NOT_FOUND, REQUEST_ENTITY_TOO_LARGE))
        "when called with an invalid request" - {
          s"should return error an error response: $error" in {
            server.stubFor {
              post(urlPathEqualTo(s"/box/${boxId.value}/notifications")).willReturn(
                aResponse()
                  .withStatus(error)
              )
            }

            val app = applicationBuilder.build()
            running(app) {
              val connector = app.injector.instanceOf[PushPullNotificationConnector]
              whenReady(connector.postNotification(boxId, messageNotificationWithBody)) {
                case Left(value)  => value.statusCode mustEqual error
                case Right(value) => fail(s"There must not be a Right (got $value instead)")
              }
            }
          }
        }
    }

  }

}

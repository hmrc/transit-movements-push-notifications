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

package uk.gov.hmrc.transitmovementspushnotifications.connectors

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.OptionValues
import org.scalatest.durations
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.time._
import play.api.test.Helpers.BAD_REQUEST
import play.api.test.Helpers.FORBIDDEN
import play.api.test.Helpers.INTERNAL_SERVER_ERROR
import play.api.test.Helpers.NOT_FOUND
import play.api.test.Helpers.OK
import play.api.test.Helpers.REQUEST_ENTITY_TOO_LARGE
import play.api.test.Helpers.await
import play.api.test.Helpers.defaultAwaitTimeout
import play.api.test.Helpers.running
import play.api.test.Helpers.status
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.transitmovementspushnotifications.config.Constants
import uk.gov.hmrc.transitmovementspushnotifications.generators.ModelGenerators
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

    "postNotification" - {

      val boxId                          = arbitraryBoxId.arbitrary.sample.value
      val payload: Source[ByteString, _] = Source.single(ByteString.fromString("""{"message":"<CC007C>data</CC007C>"}"""))
      val pushPullNotificationErrors     = List(BAD_REQUEST, FORBIDDEN, NOT_FOUND, REQUEST_ENTITY_TOO_LARGE)

      "should return unit when the post is successful" in {
        server.stubFor {
          post(urlPathEqualTo(s"/box/${boxId.value}/notifications")).willReturn(
            aResponse()
              .withStatus(OK)
          )
        }

        val app = applicationBuilder.build()
        running(app) {

          val connector = app.injector.instanceOf[PushPullNotificationConnector]
          whenReady(connector.postNotification(boxId, payload)) {
            result =>
              result mustEqual Right((): Unit)
          }

        }
      }

      for (error <- pushPullNotificationErrors)
        s"should return an UpstreamErrorResponse for the given returned $error" in {
          server.stubFor {
            post(urlPathEqualTo(s"/box/${boxId.value}/notifications")).willReturn(
              aResponse()
                .withStatus(error)
            )
          }

          val app = applicationBuilder.build()
          running(app) {
            val connector = app.injector.instanceOf[PushPullNotificationConnector]
            whenReady(connector.postNotification(boxId, payload)) {
              result =>
                result.left.get.statusCode mustEqual error
            }
          }
        }
    }

  }
}

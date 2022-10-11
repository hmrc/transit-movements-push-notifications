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

import akka.util.Timeout
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.Millis
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import play.api.test.Helpers.INTERNAL_SERVER_ERROR
import play.api.test.Helpers.NOT_FOUND
import play.api.test.Helpers.OK
import play.api.test.Helpers.running
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.transitmovementspushnotifications.config.Constants
import uk.gov.hmrc.transitmovementspushnotifications.generators.ModelGenerators
import uk.gov.hmrc.transitmovementspushnotifications.models.responses.BoxResponse
import uk.gov.hmrc.transitmovementspushnotifications.utils.GuiceWiremockSuite

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success

class PushNotificationConnectorSpec extends AnyFreeSpec with Matchers with ScalaFutures with GuiceWiremockSuite with ModelGenerators {
  override protected def portConfigKey: Seq[String] = Seq("microservice.services.push-pull-notifications-api.port")

  implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  implicit val timeout: Timeout = 5.seconds

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

          result.onComplete {
            case Failure(e) => result mustEqual Future.failed(e)
            case _          => fail("Future should have been Failure")
          }
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

          result.onComplete {
            case Failure(e) => result mustEqual Future.failed(e)
            case _          => fail("Future should have been Failure")
          }
        }
      }
    }
  }

}

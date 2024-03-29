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

package uk.gov.hmrc.transitmovementspushnotifications.config

import javax.inject.Inject
import javax.inject.Singleton
import play.api.Configuration
import io.lemonlabs.uri.Url
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@Singleton
class AppConfig @Inject() (config: Configuration, servicesConfig: ServicesConfig) {

  val pushPullUrl = Url.parse(servicesConfig.baseUrl("push-pull-notifications-api"))

  lazy val mongoRetryAttempts: Int = config.get[Int]("mongodb.retryAttempts")
  lazy val documentTtl: Long       = config.get[Long]("mongodb.timeToLiveInSeconds")

  lazy val maxPushPullPayloadSize: Int = config.get[Int]("pushPullNotificationServiceMessageLimit")

  lazy val internalAuthEnabled: Boolean = config.get[Boolean]("microservice.services.internal-auth.enabled")

}

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

package uk.gov.hmrc.transitmovementspushnotifications.repositories

import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Indexes
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.Application
import play.api.Logging
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.DefaultAwaitTimeout
import play.api.test.FutureAwaits
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.transitmovementspushnotifications.config.AppConfig
import uk.gov.hmrc.transitmovementspushnotifications.generators.ModelGenerators
import uk.gov.hmrc.transitmovementspushnotifications.models.BoxAssociation
import uk.gov.hmrc.transitmovementspushnotifications.models.MovementId
import uk.gov.hmrc.transitmovementspushnotifications.models.formats.MongoFormats
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.MongoError.DocumentNotFound
import uk.gov.hmrc.transitmovementspushnotifications.services.errors.MongoError.UnexpectedError

import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global

class BoxAssociationRepositorySpec
    extends AnyFlatSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with FutureAwaits
    with ScalaFutures
    with DefaultAwaitTimeout
    with Logging
    with DefaultPlayMongoRepositorySupport[BoxAssociation]
    with ModelGenerators
    with OptionValues {

  val lastUpdated: OffsetDateTime = OffsetDateTime.of(2022, 10, 17, 9, 1, 2, 0, ZoneOffset.UTC)
  val clock: Clock                = Clock.fixed(lastUpdated.toInstant, ZoneOffset.UTC)

  override lazy val mongoComponent: MongoComponent = {
    val databaseName: String = "test-box_association"
    val mongoUri: String     = s"mongodb://localhost:27017/$databaseName?retryWrites=false"
    MongoComponent(mongoUri)
  }

  implicit lazy val app: Application = GuiceApplicationBuilder().configure().build()
  private val appConfig              = app.injector.instanceOf[AppConfig]

  override val repository: BoxAssociationRepositoryImpl = new BoxAssociationRepositoryImpl(appConfig, mongoComponent, clock)

  "BoxAssociationRepository" should "have the correct name" in {
    repository.collectionName shouldBe "box_association"
  }

  "BoxAssociationRepository" should "have the correct column associated with the expected TTL" in {
    repository.indexes.head.getKeys shouldEqual Indexes.ascending("updated")

    repository.indexes.head.getOptions.getExpireAfter(TimeUnit.SECONDS) shouldEqual appConfig.documentTtl
  }

  "BoxAssociationRepository" should "have the correct domain format" in {
    repository.domainFormat shouldEqual MongoFormats.boxAssociationFormat
  }

  val boxAssociation = arbitraryBoxAssociation.arbitrary.sample.get

  "insert" should "add the given box association to the database" in {

    val result = await(
      repository.insert(boxAssociation).value
    )

    result should be(Right(boxAssociation))

    val firstItem = await {
      repository.collection.find(Filters.eq("_id", boxAssociation._id.value)).first().toFuture()
    }

    firstItem._id.value should be(boxAssociation._id.value)
  }

  "insert" should "add the given box to the database" in {

    val firstInsert = await(
      repository.insert(boxAssociation).value
    )

    firstInsert should be(Right(boxAssociation))

    val secondInsert = await(
      repository.insert(boxAssociation).value
    )

    secondInsert match {
      case Left(UnexpectedError(Some(_))) =>
      case _                              => fail("Excepted UnexpectedError")
    }
  }

  "update" should "return a DocumentNotFound error if it tries to update the timestamp of a non-existent movement" in forAll(arbitrary[MovementId]) {
    movementId =>
      whenReady(repository.update(movementId).value) {
        result => result should be(Left(DocumentNotFound(s"Could not find BoxAssociation with id: ${movementId.value}")))
      }
  }

  it should "return a Unit if it updates the timestamp of a movement that exists" in forAll(arbitrary[BoxAssociation]) {
    originalAssociation =>
      val eitherResult = for {
        _           <- repository.insert(originalAssociation)
        _           <- repository.update(originalAssociation._id)
        afterUpdate <- repository.getBoxAssociation(originalAssociation._id)
      } yield afterUpdate

      whenReady(eitherResult.value) {
        case Right(updateResult) => updateResult.updated should be(lastUpdated)
        case Left(_)             => fail("A left was not expected")
      }
  }

  "getBoxAssociation" should "retrieve the box association an existing movementId and update the timestamp" in {

    val boxAssociation = arbitraryBoxAssociation.arbitrary.sample.get

    val insertBox = await(
      repository.insert(boxAssociation).value
    )

    insertBox should be(Right(boxAssociation))

    val result = await(
      repository.getBoxAssociation(boxAssociation._id).value
    )

    result should be(Right(boxAssociation))
  }

  "getBoxAssociation" should "return a DocumentNotFound error for a movementId that is not in the database" in {
    val unknownMovementId = MovementId("Unknown Movement Id")
    val result            = await(
      repository.getBoxAssociation(unknownMovementId).value
    )

    result should be(Left(DocumentNotFound(s"Could not find BoxAssociation with id: ${unknownMovementId.value}")))
  }

}

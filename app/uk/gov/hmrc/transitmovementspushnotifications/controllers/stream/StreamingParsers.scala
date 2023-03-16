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

package uk.gov.hmrc.transitmovementspushnotifications.controllers.stream

import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.implicits.catsSyntaxMonadError
import play.api.Logging
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc.Action
import play.api.mvc.ActionBuilder
import play.api.mvc.BaseControllerHelpers
import play.api.mvc.BodyParser
import play.api.mvc.Request
import play.api.mvc.Result
import uk.gov.hmrc.transitmovementspushnotifications.controllers.errors.PresentationError

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal

trait StreamingParsers {
  self: BaseControllerHelpers with Logging =>

  implicit val materializer: Materializer

  implicit val materializerExecutionContext: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  lazy val streamFromMemory: BodyParser[Source[ByteString, _]] = BodyParser {
    _ =>
      Accumulator.source[ByteString].map(Right.apply)
  }

  implicit class ActionBuilderStreamHelpers(actionBuilder: ActionBuilder[Request, _]) {

    /** Updates the [[Source]] in the [[Request]] with a version that can be used
      * multiple times via the use of a temporary file.
      *
      * @param block The code to use the with the reusable source
      * @return An [[Action]]
      */
    // Implementation note: Tried to use the temporary file parser but it didn't pass the "single use" tests.
    // Doing it like this ensures that we can make sure that the source we pass is the file based one,
    // and only when it's ready.
    def stream(
      block: Request[Source[ByteString, _]] => Future[Result]
    )(implicit temporaryFileCreator: TemporaryFileCreator): Action[Source[ByteString, _]] =
      actionBuilder.async(streamFromMemory) {
        request =>
          // This is outside the for comprehension because we need access to the file
          // if the rest of the futures fail, which we wouldn't get if it was in there.
          Future
            .fromTry(Try(temporaryFileCreator.create()))
            .flatMap {
              file =>
                (for {
                  _      <- request.body.runWith(FileIO.toPath(file))
                  result <- block(request.withBody(FileIO.fromPath(file)))
                } yield result)
                  .attemptTap {
                    _ =>
                      file.delete()
                      Future.successful(())
                  }
            }
            .recover {
              case NonFatal(ex) =>
                logger.error(s"Failed call: ${ex.getMessage}", ex)
                Status(INTERNAL_SERVER_ERROR)(Json.toJson(PresentationError.internalServerError(cause = Some(ex))))
            }
      }
  }

}

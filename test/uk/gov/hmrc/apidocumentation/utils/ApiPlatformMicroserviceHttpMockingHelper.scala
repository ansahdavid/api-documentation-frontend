/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.apidocumentation.utils

import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import uk.gov.hmrc.apidocumentation.connectors.ApiPlatformMicroserviceConnector.{Params, definitionsUrl, noParams, queryParams}
import uk.gov.hmrc.apidocumentation.models.APIDefinition
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.Future

trait ApiPlatformMicroserviceHttpMockingHelper {
  val mockHttpClient: HttpClient
  val apiPlatformMicroserviceBaseUrl: String


  def whenGetAllDefinitionsByEmail(email: Option[String])(definitions: APIDefinition*): Unit = {
    val url = definitionsUrl(apiPlatformMicroserviceBaseUrl)
    when(
      mockHttpClient.GET[Seq[APIDefinition]](
        eqTo(url),
        eqTo(email.map(e => queryParams(Some(e))).getOrElse(noParams))
      )
        (any(), any(), any())
    )
      .thenReturn(Future.successful(definitions.toSeq))
  }

  def whenGetAllDefinitionsFails(exception: Throwable): Unit = {
    val url = definitionsUrl(apiPlatformMicroserviceBaseUrl)
    when(
      mockHttpClient.GET[Seq[APIDefinition]](
        eqTo(url),
        any[Params]
      )
        (any(), any(), any())
    )
      .thenReturn(Future.failed(exception))
  }
}
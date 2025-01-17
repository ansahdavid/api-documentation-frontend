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

package uk.gov.hmrc.apidocumentation.connectors

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.http.HeaderNames
import play.api.libs.ws.{WSClient, WSProxyServer, WSRequest => PlayWSRequest}
import uk.gov.hmrc.http.Authorization
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import uk.gov.hmrc.play.http.ws.{WSProxy, WSProxyConfiguration}

@Singleton
class ProxiedHttpClient @Inject()(config: Configuration,
                                  httpAuditing: HttpAuditing,
                                  wsClient: WSClient,
                                  actorSystem: ActorSystem)
  extends DefaultHttpClient(config, httpAuditing, wsClient, actorSystem) with WSProxy {

  val authorization: Option[Authorization] = None
  val apiKeyHeader: Option[String] = None

  def withHeaders(bearerToken: String, apiKey: String = ""): ProxiedHttpClient = {
    new ProxiedHttpClient(config, httpAuditing, wsClient, actorSystem) {
      override val authorization = Some(Authorization(s"Bearer $bearerToken"))
      override val apiKeyHeader = if (apiKey.isEmpty) None else Some(apiKey)
    }
  }

  override def wsProxyServer: Option[WSProxyServer] = WSProxyConfiguration("proxy", config)

  override def buildRequest[A](url: String, headers: Seq[(String, String)]): PlayWSRequest = {
    val extraHeaders: Seq[(String,String)] = headers ++ 
      authorization.map(v => (HeaderNames.AUTHORIZATION -> v.value)).toSeq ++
      apiKeyHeader.map(v => ProxiedHttpClient.API_KEY_HEADER_NAME -> v).toSeq ++
      Seq(ProxiedHttpClient.ACCEPT_HMRC_JSON_HEADER)

    super.buildRequest(url, extraHeaders)
  }
}

object ProxiedHttpClient {
  val API_KEY_HEADER_NAME = "x-api-key"
  val ACCEPT_HMRC_JSON_HEADER = HeaderNames.ACCEPT -> "application/hmrc.vnd.1.0+json"
}


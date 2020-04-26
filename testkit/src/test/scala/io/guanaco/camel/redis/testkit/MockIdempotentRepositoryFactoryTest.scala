/*
 * Copyright 2020 - anova r&d bvba
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
package io.guanaco.camel.redis.testkit

import org.apache.camel.{CamelExecutionException, RoutesBuilder}
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.test.junit4.CamelTestSupport
import org.junit.Test

class MockIdempotentRepositoryFactoryTest extends CamelTestSupport {

  import MockIdempotentRepositoryFactoryTest._

  /*
   * Test: check if idempotency is handled correctly
   */
  @Test
  def testIdempotency(): Unit = {
    getMockEndpoint(MockIdempotent).expectedMessageCount(2)

    sendMessage("first", withHeader = true)
    sendMessage("first", withHeader = true)
    sendMessage("second", withHeader = true)

    assertMockEndpointsSatisfied()
  }

  /*
   * Test: assertion error if business id expression doesn't work
   */
  @Test(expected = classOf[CamelExecutionException])
  def testMissingBusinessIdHeader(): Unit = {
    sendMessage("boom!", withHeader = false)
  }

  def sendMessage(body: String, withHeader: Boolean) =
    if (withHeader) template.sendBodyAndHeader(DirectStart, body, BusinessIdHeader, body)
    else template.sendBody(DirectStart, body)


  override def createRouteBuilder(): RoutesBuilder = new RouteBuilder() {
    override def configure(): Unit = {
      //format: off
      from(DirectStart)
        .idempotentConsumer(body(), MockIdempotentRepositoryFactory.create(header(BusinessIdHeader)))
          .to(MockIdempotent)
      //format: on
    }
  }
}

object MockIdempotentRepositoryFactoryTest {

  val DirectStart = "direct:start"

  val MockIdempotent = "mock:idempotent"

  val BusinessIdHeader = "TestBusinessIdHeader"

}

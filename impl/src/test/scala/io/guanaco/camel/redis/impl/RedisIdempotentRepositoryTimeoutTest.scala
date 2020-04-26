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
package io.guanaco.camel.redis.impl

import org.apache.camel.CamelContext
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.impl.ExplicitCamelContextNameStrategy
import org.apache.camel.model.language.HeaderExpression
import org.apache.camel.test.junit4.CamelTestSupport
import org.junit.Assert._
import org.junit._
import redis.clients.jedis.Jedis
import redis.embedded.RedisServer

import scala.concurrent.duration._

class RedisIdempotentRepositoryTimeoutTest extends CamelTestSupport {

  import RedisIdempotentRepositoryTimeoutTest._
  import io.guanaco.camel.redis.IdempotentRepositoryFactory._

  val redisIdempotentRepositoryFactory = new RedisIdempotentRepositoryFactory("0.0.0.0", RedisPort, RedisDatabase)
  val redisIdempotentRepository = redisIdempotentRepositoryFactory.create(new HeaderExpression(BusinessIdHeader), Some(Timeout))

  val jedis = new Jedis(s"redis://localhost:${RedisPort}/${RedisDatabase}")

  @After
  def stopFactory(): Unit = redisIdempotentRepositoryFactory.stop()

  @Test
  def testTimeout(): Unit = {
    def await(remaining: Duration, timeout: Duration)(condition: => Boolean): Unit =
      if (!condition) {
        if (remaining < timeout) fail("Condition not reached within the timeout")
        else {
          Thread.sleep(timeout.toMillis)
          await(remaining - timeout, timeout)(condition)
        }
      }

    val all = getMockEndpoint("mock:all")

    context.addRoutes(createTestRoutes(RouteId))
    template.sendBody("direct:start", 1)

    all.expectedMessageCount(1)

    assertNotNull(jedis.hget(s"${ContextName}:${RouteId}:key-is-1", RedisIdempotentRepository.valueIdentifier))

    await(2 * Timeout, 100 milli) {
      jedis.hget(s"${ContextName}:${RouteId}:key-is-1", RedisIdempotentRepository.valueIdentifier) == null
    }
  }

  override def createCamelContext(): CamelContext = {
    val context = super.createCamelContext()
    context.setNameStrategy(new ExplicitCamelContextNameStrategy(ContextName))
    context
  }

  def createTestRoutes(routeId: String): RouteBuilder = new RouteBuilder() {
    override def configure(): Unit = {
      onException(classOf[RuntimeException]).continued(true)
      from("direct:start")
        .routeId(routeId)
        .setHeader(BusinessIdHeader, simple("key-is-${body}"))
        .idempotentConsumer(body(), redisIdempotentRepository)
        .to("mock:all")
    }
  }

}

object RedisIdempotentRepositoryTimeoutTest {

  val ContextName = "my-camel-context"
  val RouteId = "my-camel-route"

  import org.apache.camel.test.AvailablePortFinder

  val RedisPort = AvailablePortFinder.getNextAvailable

  val RedisDatabase = 0

  private val redisServer = new RedisServer(RedisPort)

  @BeforeClass
  def startServer(): Unit = redisServer.start()

  @AfterClass
  def stopServer(): Unit = redisServer.stop()

  val Timeout = 1000 milliseconds

}


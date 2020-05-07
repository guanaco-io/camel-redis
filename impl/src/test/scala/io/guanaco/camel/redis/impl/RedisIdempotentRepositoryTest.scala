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

import java.util

import io.guanaco.camel.redis.IdempotentRepositoryFactory._
import io.guanaco.camel.redis.impl.RedisIdempotentRepository._
import org.apache.camel.{CamelContext, RoutesBuilder}
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.impl.ExplicitCamelContextNameStrategy
import org.apache.camel.model.language.SimpleExpression
import org.apache.camel.test.AvailablePortFinder
import org.apache.camel.test.junit4.CamelTestSupport
import org.junit.Assert._
import org.junit._
import redis.clients.jedis.Jedis
import redis.embedded.RedisServer

import scala.util.Random

class RedisIdempotentRepositoryTest extends CamelTestSupport {

  import RedisIdempotentRepositoryTest._

  val redisIdempotentRepositoryFactory = new RedisIdempotentRepositoryFactory("0.0.0.0", RedisPort, RedisDatabase)
  val redisIdempotentRepository = redisIdempotentRepositoryFactory.create(new SimpleExpression("body"))

  val jedis = new Jedis(s"redis://localhost:${RedisPort}/${RedisDatabase}")

  @Test
  def testIdempotencyEager(): Unit = testIdempotency(EagerScenario)

  @Test
  def testIdempotencyLazy(): Unit = testIdempotency(LazyScenario)

  def testIdempotency(scenario: Scenario) = {
    // send a batch of message
    def sendBatchOfMessages() = {
      (1 to BatchSize) foreach { int =>
        template.sendBody(scenario.startEndpoint, int)
      }
    }

    // mock endpoints
    val all = getMockEndpoint("mock:all")
    all.expectedMessageCount(BatchSize)

    val idempotent = getMockEndpoint("mock:idempotent")
    idempotent.expectedMinimumMessageCount(1)

    // send a first batch of messages
    sendBatchOfMessages()
    all.assertIsSatisfied()

    // validate redis contents
    assertEquals("Number of keys in the store should match the number of successfully handled exchanges",
      idempotent.getReceivedCounter, scenario.keys(jedis).size())

    // reset mock endpoint before sending a second batch
    all.reset()
    all.expectedMessageCount(BatchSize)

    // send a second batch of messages
    sendBatchOfMessages()
    all.assertIsSatisfied()

    // validate idempotent endpoint and redis content
    assertTrue(s"We should never have more than ${BatchSize} different messages",
      idempotent.getReceivedCounter <= BatchSize)
    assertEquals("Number of keys in the store should match the number of successfully handled exchanges",
      idempotent.getReceivedCounter, jedis.keys(s"${ContextName}:${scenario.routeId}:*").size())

    import scala.collection.JavaConverters._
    val keys = jedis.keys(s"${ContextName}:${scenario.routeId}:*").asScala
     keys foreach  { k =>
      assertNotNull(jedis.hget(k, processedIdentifier))
    }

    all.reset()
    all.expectedMessageCount(keys.size)

    //get the modified timestamps of the current records
    val values = keys map { k =>
      val values = jedis.hget(k, modifiedIdentifier)
      template.sendBodyAndHeader(scenario.startEndpoint, jedis.hget(k, valueIdentifier), OverrideIdemPotentProcessingHeader, true)
      values
    }

    all.assertIsSatisfied()

    //modified timestamps should have changed due to the override header
    keys map { k =>
      val processed = jedis.hget(k, modifiedIdentifier)
      assertFalse(values.contains(processed))
    }
  }

  override def createCamelContext(): CamelContext = {
    val context = super.createCamelContext()
    context.setNameStrategy(new ExplicitCamelContextNameStrategy(ContextName))
    context
  }


  override def createRouteBuilder(): RoutesBuilder = new RouteBuilder() {
    def createTestRoutes(scenario: Scenario, eager: Boolean): Unit = {
      // format: off
      from(scenario.startEndpoint)
          .routeId(scenario.routeId)
          .idempotentConsumer(body(), redisIdempotentRepository).eager(eager)
            .bean(Helper(), "randomFailure")
            .to("mock:idempotent")
          .end()
          .to("mock:all")
      // format: on
    }

    override def configure(): Unit = {
      onException(classOf[RuntimeException]).continued(true)

      createTestRoutes(LazyScenario, false)
      createTestRoutes(EagerScenario, true)
    }
  }



  case class Helper() {
    def randomFailure(fsd: String): Unit = {
      if (Random.nextInt(4) < 1) throw new RuntimeException("It's just a 25% chance, how can this happen!!!")
    }
  }

}

object RedisIdempotentRepositoryTest {

  val ContextName = "my-camel-context"

  sealed trait Scenario {
    val routeId: String
    val startEndpoint: String

    def keys(jedis: Jedis): util.Set[String] = jedis.keys(s"${ContextName}:${routeId}:*")
  }

  case object EagerScenario extends Scenario {
    val routeId = EagerRouteId
    val startEndpoint = EagerSedaEndpoint
  }

  case object LazyScenario extends Scenario {
    val routeId = LazyRouteId
    val startEndpoint = LazySedaEndpoint
  }

  val EagerRouteId = "my-eager-camel-route"
  val LazyRouteId = "my-lazy-camel-route"

  val EagerSedaEndpoint = "seda:eager"
  val LazySedaEndpoint = "seda:lazy"

  val BatchSize = 20

  val RedisPort = AvailablePortFinder.getNextAvailable

  val RedisDatabase = 0

  private val redisServer = new RedisServer(RedisPort)

  @BeforeClass
  def startServer(): Unit = redisServer.start()

  @AfterClass
  def stopServer(): Unit = redisServer.stop()

}

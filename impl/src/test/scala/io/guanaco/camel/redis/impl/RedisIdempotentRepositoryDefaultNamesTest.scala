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

import org.apache.camel.builder.RouteBuilder
import org.apache.camel.model.language.HeaderExpression
import org.apache.camel.test.junit4.CamelTestSupport
import org.apache.log4j.{Level, Logger}
import org.junit.Assert._
import org.junit._
import redis.embedded.RedisServer

class RedisIdempotentRepositoryDefaultNamesTest extends CamelTestSupport {

  import RedisIdempotentRepositoryDefaultNamesTest._
  import io.guanaco.camel.redis.IdempotentRepositoryFactory._

  val redisIdempotentRepositoryFactory = new RedisIdempotentRepositoryFactory("0.0.0.0", RedisPort, RedisDatabase)
  val redisIdempotentRepository = redisIdempotentRepositoryFactory.create(new HeaderExpression(BusinessIdHeader))

  @After
  def stopFactory(): Unit = redisIdempotentRepositoryFactory.stop()

  @Test
  def testWarningLogWhenDefaultNamesAreUsed(): Unit = {
    val all = getMockEndpoint("mock:all")
    context.addRoutes(createTestRoutes())
    val appender = new TestAppender
    val logger = Logger.getRootLogger
    logger.addAppender(appender)

    template.sendBody("direct:start", 1)

    val log = appender.getLog
    log.contains()
    import scala.collection.JavaConverters._
    for (logEvent <- appender.getLog.asScala) {
      if (logEvent.getLevel.equals(Level.WARN)) {
        assertTrue(logEvent.getMessage.asInstanceOf[String].contains("got used to determine redis key"))
      }
    }
  }

  def createTestRoutes(): RouteBuilder = new RouteBuilder() {
    override def configure(): Unit = {
      onException(classOf[RuntimeException]).continued(true)
      from("direct:start")
        .setHeader(BusinessIdHeader, simple("key-is-${body}"))
        .idempotentConsumer(body(), redisIdempotentRepository)
        .to("mock:all")
    }
  }

  import java.util

  import org.apache.log4j.AppenderSkeleton
  import org.apache.log4j.spi.LoggingEvent

  class TestAppender extends AppenderSkeleton {
    final private val log = new util.ArrayList[LoggingEvent]

    override def requiresLayout = false

    override protected def append(loggingEvent: LoggingEvent): Unit = {
      log.add(loggingEvent)
    }

    override def close(): Unit = {
    }

    def getLog = new util.ArrayList[LoggingEvent](log)
  }

}

object RedisIdempotentRepositoryDefaultNamesTest {

  import org.apache.camel.test.AvailablePortFinder

  val RedisPort = AvailablePortFinder.getNextAvailable

  val RedisDatabase = 0

  private val redisServer = new RedisServer(RedisPort)

  @BeforeClass
  def startServer(): Unit = redisServer.start()

  @AfterClass
  def stopServer(): Unit = redisServer.stop()

}

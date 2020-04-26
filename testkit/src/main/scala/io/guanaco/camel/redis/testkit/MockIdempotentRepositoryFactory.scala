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

import io.guanaco.camel.redis.IdempotentRepositoryFactory
import org.apache.camel.{Exchange, Expression}
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository
import org.apache.camel.spi.ExchangeIdempotentRepository
import org.apache.camel.test.junit4.CamelTestSupport
import org.junit.Assert.assertNotNull
import org.slf4j.LoggerFactory

import scala.concurrent.duration.Duration

/**
 * Convenience trait to create an [[IdempotentRepositoryFactory]] instance for your Camel unit tests.
 *
 * The [[org.apache.camel.spi.IdempotentRepository]] instances it creates, are based on [[MemoryIdempotentRepository]].
 * Additionally, it will also evaluate the business id expression on every idempotent repository operation
 */
object MockIdempotentRepositoryFactory extends IdempotentRepositoryFactory {

  private[testkit] val Logger = LoggerFactory.getLogger(classOf[MockIdempotentRepositoryFactory])

  override def create(expression: Expression, timeout: Option[Duration]): ExchangeIdempotentRepository[String] = {
    Logger.info(s"Creating a mock idempotent repository with expression ${expression} and a timeout of ${timeout}")
    new TestIdempotentRepository(expression)
  }

  // the test idempotent repository implementation
  private class TestIdempotentRepository(expression: Expression) extends MemoryIdempotentRepository with ExchangeIdempotentRepository[String] {

    override def add(exchange: Exchange, key: String): Boolean = {
      assertBusinessIdExpression(exchange)
      super.add(key)
    }

    override def contains(exchange: Exchange, key: String): Boolean = {
      assertBusinessIdExpression(exchange)
      contains(key)
    }

    override def remove(exchange: Exchange, key: String): Boolean = {
      assertBusinessIdExpression(exchange)
      remove(key)
    }

    override def confirm(exchange: Exchange, key: String): Boolean = {
      assertBusinessIdExpression(exchange)
      confirm(key)
    }

    private def assertBusinessIdExpression(exchange: Exchange): Unit =
      if (expression.evaluate(exchange, classOf[String]) == null)
        throw new BusinessIdUnavailableException("Business id expression should not return null")

  }

  class BusinessIdUnavailableException(message: String) extends RuntimeException
}

// private class for defining the logger
private class MockIdempotentRepositoryFactory

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
package io.guanaco.camel.redis

import org.apache.camel.Expression
import org.apache.camel.model.language.HeaderExpression
import org.apache.camel.spi.ExchangeIdempotentRepository

import scala.concurrent.duration.Duration

/**
 * Factory trait to create [[ExchangeIdempotentRepository]] instances that also keeps track of the business id of an exchange while checking idempotency.
 */
trait IdempotentRepositoryFactory {

  import IdempotentRepositoryFactory._

  /**
   * Create an idempotent repository for a given environment, using an expression to determine a business id for the exchange and a timeout for the keys to expire.
   */
  def create(expression: Expression = DefaultExpression, timeout: Option[Duration] = None): ExchangeIdempotentRepository[String]

}

object IdempotentRepositoryFactory {

  /**
   * Camel Exchange header that contains the business id of an exchange
   */
  final val BusinessIdHeader = "GuanacoCamelRedisIdemPotentBusinessId"

  private[redis] final val DefaultExpression = new HeaderExpression(BusinessIdHeader)

}

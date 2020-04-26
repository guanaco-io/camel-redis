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

import java.net.URI

import io.guanaco.camel.redis.IdempotentRepositoryFactory
import org.apache.camel.Expression
import org.apache.camel.spi.ExchangeIdempotentRepository
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import redis.clients.jedis.JedisPool

import scala.concurrent.duration.Duration

/**
  * [[IdempotentRepositoryFactory]] implementation that uses Redis as the backing store
  *
  * @param host the Redis host
  * @param port the Redis port
  * @param db the Redis db
  */
class RedisIdempotentRepositoryFactory(host: String, port: Int, db: Int) extends IdempotentRepositoryFactory {

  import RedisIdempotentRepositoryFactory._

  // Jedis connection pool
  val pool = {
    val config = new GenericObjectPoolConfig()
    config.setMaxTotal(MaxPoolActiveSize)
    config.setMaxIdle(MaxPoolIdleSize)
    config.setMinIdle(MinPoolIdleSize)
    val uri = URI.create(s"redis://${host}:${port}/${db}")
    new JedisPool(config, uri)
  }

  /**
    * Create an idempotent repository for a given environment, using an expression to determine a business id for the exchange.
    */
  override def create(expression: Expression, timeout: Option[Duration]): ExchangeIdempotentRepository[String] = {
    new RedisIdempotentRepository(pool, expression, timeout)
  }

  /**
    * Stop the idempotent repository factory by removing/closing all the connection pools
    */
  def stop(): Unit = pool.close()
}

object RedisIdempotentRepositoryFactory {

  final val MaxPoolActiveSize = 32
  final val MaxPoolIdleSize = 8
  final val MinPoolIdleSize = 4

}

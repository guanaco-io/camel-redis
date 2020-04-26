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

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import org.apache.camel.spi.ExchangeIdempotentRepository
import org.apache.camel.{Exchange, Expression}
import org.slf4j.LoggerFactory
import redis.clients.jedis.{Jedis, JedisPool}

import scala.concurrent.duration.Duration

/**
  * Redis-based idempotent repository implementation.
  * Use [[RedisIdempotentRepositoryFactory]] instead of using this class directly.
  */
private[impl] class RedisIdempotentRepository(pool: JedisPool, expression: Expression, timeout: Option[Duration]) extends ExchangeIdempotentRepository[String] {

  import RedisIdempotentRepository._

  val format = DateTimeFormatter.ISO_OFFSET_DATE_TIME

  override def add(exchange: Exchange, hash: String): Boolean = withJedis { implicit jedis =>
    val redisKey = keyFor(exchange)
    if (contains(exchange, hash)) false
    else {
      Logger.debug(s"Setting value for ${redisKey} to ${hash}")
      hset(redisKey, hash)
      refresh(redisKey)
      true
    }
  }

  override def contains(exchange: Exchange, hash: String): Boolean = withJedis { implicit jedis =>
    val redisKey = keyFor(exchange)
    Option(jedis.hget(redisKey, valueIdentifier)) match {
      case Some(current) if current == hash => {
        Logger.debug(s"Value for ${redisKey} already equals ${hash}")
        refresh(redisKey)
        true
      }
      case _ => false
    }
  }

  override def remove(exchange: Exchange, key: String): Boolean = withJedis { implicit jedis =>
    val redisKey = keyFor(exchange)
    Logger.debug(s"Removing redis key ${redisKey}")
    jedis.del(redisKey) > 0
  }

  override def confirm(exchange: Exchange, key: String): Boolean = {
    // graciously ignore this
    true
  }

  private def refresh(key: String): Unit = withJedis { jedis =>
    timeout map { value =>
      jedis.expire(key, value.toSeconds.toInt)
    }
  }

  override def add(key: String): Boolean = ???

  override def contains(key: String): Boolean = ???

  override def remove(key: String): Boolean = ???

  override def confirm(key: String): Boolean = ???

  override def clear(): Unit = ???

  override def start(): Unit = {
    // nothing to do - connection pool is managed by RedisIdempotentRepositoryFactory
  }

  override def stop(): Unit = {
    // nothing to do - connection pool is managed by RedisIdempotentRepositoryFactory
  }

  private def withJedis[T](operation: (Jedis => T)): T = {
    val jedis = pool.getResource()
    try {
      operation(jedis)
    } finally {
      jedis.close()
    }
  }

  private def keyFor(exchange: Exchange): String = {
    val contextName = exchange.getContext.getName
    if (isContextNameDefault(contextName)) {
      Logger.warn(s"A default context name got used to determine redis key : ${contextName}")
    }

    val routeName = exchange.getFromRouteId
    if (isRouteNameDefault(routeName)) {
      Logger.warn(s"A default route id got used to determine redis key ${routeName}")
    }

    Option(expression.evaluate(exchange, classOf[String])) match {
      case Some(id) => s"${contextName}:${routeName}:${id}"
      case None => unableToDetermineBusinssId(exchange)
    }
  }

  private def hset(redisKey: String, hash: String) = withJedis { implicit jedis =>
    if (jedis.exists(redisKey)) {
      jedis.hset(redisKey, modifiedIdentifier, format.format(ZonedDateTime.now()))
    } else {
      jedis.hset(redisKey, createdIdentifier, format.format(ZonedDateTime.now()))
    }
    jedis.hset(redisKey, valueIdentifier, hash)
  }

}

object RedisIdempotentRepository {

  val Logger = LoggerFactory.getLogger(classOf[RedisIdempotentRepository])

  val valueIdentifier = "value"
  val createdIdentifier = "created"
  val modifiedIdentifier = "modified"

  class RedisIdempotentRepositoryOperationFailed(message: String) extends RuntimeException(message)

  def unableToDetermineBusinssId(exchange: Exchange) =
    throw new RedisIdempotentRepositoryOperationFailed(s"Unable to determine business id for exchange ${exchange.getExchangeId}")

  val defaultContextName = """^camel-\d+$""".r
  val defaultRouteName = """^route\d+$""".r

  def isContextNameDefault(contextName: String): Boolean = {
    defaultContextName.findFirstIn(contextName) match {
      case Some(contextName) => true
      case _ => false
    }
  }

  def isRouteNameDefault(routeName: String): Boolean = {
    defaultRouteName.findFirstIn(routeName) match {
      case Some(routeName) => true
      case _ => false
    }
  }

}
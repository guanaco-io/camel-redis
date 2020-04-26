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
import sbt._

object Dependencies {

  object Version {
    val camel = "2.21.3"
    val junit = "4.12"
    val junit_if = "0.11"
    val slf4j = "1.7.25"
    val jedis = "2.9.0"
    val embedded_redis = "0.6"
    val jaxb = "2.3.0"
    val activation = "1.2.0"
  }

  val common = Seq(
    "org.slf4j"            %  "slf4j-api"       % Version.slf4j,
    "org.apache.camel"     %  "camel-core"      % Version.camel,
    "com.sun.activation"   %  "javax.activation" % Version.activation,
    "javax.xml.bind"       %  "jaxb-api"        % Version.jaxb,
    "com.sun.xml.bind"     %  "jaxb-core"       % Version.jaxb,
    "com.sun.xml.bind"     %  "jaxb-impl"       % Version.jaxb
  )

  val tests = Seq(
    "junit"                %  "junit"           % Version.junit      % Test,
    "com.novocode"         %  "junit-interface" % Version.junit_if   % Test exclude("junit", "junit-dep"),
    "org.slf4j"            %  "slf4j-log4j12"   % Version.slf4j      % Test
  )

  lazy val impl = tests ++ common ++ Seq(
    "org.apache.camel"     %  "camel-core"      % Version.camel,
    "redis.clients"        %  "jedis"           % Version.jedis,

    "org.apache.camel"     %  "camel-test"      % Version.camel      % Test,
    "com.github.kstyrc"    %  "embedded-redis"  % Version.embedded_redis % Test
  )

  lazy val api = common

  lazy val testkit = common ++ tests ++ Seq(
    "org.apache.camel"     %  "camel-test"      % Version.camel
  )

}

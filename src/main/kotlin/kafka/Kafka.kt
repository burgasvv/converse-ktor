package org.burgas.kafka

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.github.flaxoos.ktor.server.plugins.kafka.TopicName
import io.github.flaxoos.ktor.server.plugins.kafka.common
import io.github.flaxoos.ktor.server.plugins.kafka.components.fromRecord
import io.github.flaxoos.ktor.server.plugins.kafka.consumer
import io.github.flaxoos.ktor.server.plugins.kafka.consumerConfig
import io.github.flaxoos.ktor.server.plugins.kafka.consumerRecordHandler
import io.github.flaxoos.ktor.server.plugins.kafka.installKafka
import io.github.flaxoos.ktor.server.plugins.kafka.producer
import io.github.flaxoos.ktor.server.plugins.kafka.registerSchemas
import io.github.flaxoos.ktor.server.plugins.kafka.topic
import io.ktor.client.HttpClient
import io.ktor.server.application.*
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.burgas.dto.IdentityResponse

fun Application.configureKafka() {

    installKafka {
        this.schemaRegistryUrl = "http://localhost:8081"

        val identityTopic = TopicName("identity-topic")
        this.topic(identityTopic) {
            this.partitions = 1
            this.replicas = 1
        }

        this.common {
            this.bootstrapServers = listOf("localhost:9092")
        }

        this.producer {
            this.keySerializerClass = StringSerializer::class.java
            this.valueSerializerClass = KafkaAvroSerializer::class.java
        }

        this.consumer {
            this.keyDeserializerClass = StringDeserializer::class.java
            this.valueDeserializerClass = KafkaAvroDeserializer::class.java
        }

        this.registerSchemas {
            this.using { HttpClient() }
            IdentityResponse::class at identityTopic
        }

        this.consumerConfig {
            this.consumerRecordHandler(identityTopic) { record ->
                val identityResponse = fromRecord<IdentityResponse>(record = record.value())
                println("${record.topic()} :: $identityResponse")
            }
        }
    }
}
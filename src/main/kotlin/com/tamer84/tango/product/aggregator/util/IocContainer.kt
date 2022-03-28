package com.tamer84.tango.product.aggregator.util


import com.tamer84.tango.product.aggregator.repo.EventRepo
import com.tamer84.tango.product.aggregator.repo.SnapshotRepo
import com.tamer84.tango.product.aggregator.service.AggregateService
import com.tamer84.tango.product.aggregator.service.MetadataService
import com.tamer84.tango.product.aggregator.service.SnapshotService
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.core.retry.RetryPolicy
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.time.Duration

object IocContainer {

    // https://aws.amazon.com/blogs/developer/aws-sdk-for-java-2-x-released/
    // https://aws.amazon.com/blogs/database/tuning-aws-java-sdk-http-request-settings-for-latency-aware-amazon-dynamodb-applications/
    private val apacheHttpClient = ApacheHttpClient.builder()
            .maxConnections(2048)
            .connectionAcquisitionTimeout(Duration.ofMillis(500))
            .connectionMaxIdleTime(Duration.ofMillis(500))
            .connectionTimeout(Duration.ofMillis(5000))
            .socketTimeout(Duration.ofMillis(5000))
            .build()

    private val timeoutNoRetryConfiguration = ClientOverrideConfiguration.builder()
            .apiCallTimeout(Duration.ofSeconds(60))
            .apiCallAttemptTimeout(Duration.ofSeconds(10))
            .retryPolicy(RetryPolicy.none())
            .addExecutionInterceptor(DynamoMetricInterceptor())
            .build()

    private val dynamoDbClient = DynamoDbClient.builder()
            .overrideConfiguration(timeoutNoRetryConfiguration)
            .httpClient(apacheHttpClient)
            .region(Region.EU_CENTRAL_1)
            .build()


    private val snapshotRepo = SnapshotRepo(dynamoDbClient)
    val snapshotService = SnapshotService(snapshotRepo)

    private val eventRepo = EventRepo(dynamoDbClient)
    val aggregateService = AggregateService(eventRepo, snapshotService)

    val metadataService = MetadataService(eventRepo)

}

package com.machinocare.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;

import java.util.UUID;

/**
 * Spring Integration MQTT Configuration.
 * 
 * Establishes a persistent, resilient connection to the Eclipse Mosquitto broker
 * and subscribes to the MachinoCare v1.0 topic taxonomy.
 */
@Configuration
@EnableIntegration
@IntegrationComponentScan
public class MqttConfig {

    @Value("${machinocare.mqtt.broker-url:tcp://localhost:1883}")
    private String brokerUrl;

    @Value("${machinocare.mqtt.topics.status:machinocare/v1/devices/+/status}")
    private String statusTopic;

    @Value("${machinocare.mqtt.topics.telemetry:machinocare/v1/devices/+/telemetry}")
    private String telemetryTopic;

    @Value("${machinocare.mqtt.topics.condition:machinocare/v1/devices/+/condition}")
    private String conditionTopic;

    @Value("${machinocare.mqtt.completion-timeout-ms:5000}")
    private int completionTimeoutMs;

    /**
     * Configures the Eclipse Paho MQTT client connection parameters.
     */
    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);
        factory.setConnectionOptions(options);
        return factory;
    }

    /**
     * Direct in-memory channel carrying inbound MQTT messages to consumers.
     */
    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    /**
     * Inbound message-driven channel adapter that listens to Mosquitto and forwards
     * raw messages into mqttInputChannel.
     */
    @Bean
    public MessageProducer inboundMqttAdapter(MqttPahoClientFactory mqttClientFactory) {
        String clientId = "machinocare-backend-" + UUID.randomUUID().toString().substring(0, 8);
        String[] topics = new String[]{statusTopic, telemetryTopic, conditionTopic};

        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(brokerUrl, clientId, mqttClientFactory, topics);

        adapter.setCompletionTimeout(completionTimeoutMs);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }
}

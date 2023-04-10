package com.google.bos.udmi.service.messaging;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.udmi.util.JsonUtil.stringify;

import com.google.udmi.util.Common;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.NotImplementedException;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import udmi.schema.Basic;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Transport;
import udmi.schema.Envelope;
import udmi.schema.MessageConfiguration;

/**
 * Simple pipe implementation that uses an mqtt broker.
 */
public class SimpleMqttPipe extends MessageBase {

  private static final int INITIALIZE_TIME_MS = 1000;
  private static final int PUBLISH_THREAD_COUNT = 2;
  private static final String TOPIC_FORMAT = "%s/%s/%s";
  private static final Object TOPIC_WILDCARD = "+";
  private static final String BROKER_URL_FORMAT = "%s://%s:%s";
  private static final Object EXCEPTION_TYPE = "exception";
  private final MqttClient mqttClient;
  private final String clientId;
  private final String namespace;

  /**
   * Create new pipe instance for the given config.
   */
  public SimpleMqttPipe(MessageConfiguration config) {
    clientId = makeClientId();
    namespace = config.namespace;
    mqttClient = connectMqttClient(config.endpoint);
  }

  static MessagePipe from(MessageConfiguration config) {
    return new SimpleMqttPipe(config);
  }

  protected void publishBundle(Bundle bundle) {
    try {
      mqttClient.publish(getMqttTopic(bundle), getMqttMessage(bundle));
    } catch (Exception e) {
      throw new RuntimeException("While publishing to mqtt client", e);
    }
  }

  private MqttClient connectMqttClient(EndpointConfiguration endpoint) {
    String broker = makeBrokerUrl(endpoint);
    String message = String.format("Connecting new mqtt client %s on %s", clientId, broker);
    try {
      info(message);
      MqttClient client = new MqttClient(broker, clientId, new MemoryPersistence());

      client.setCallback(new MqttCallbackHandler());
      client.setTimeToWait(INITIALIZE_TIME_MS);

      MqttConnectOptions options = new MqttConnectOptions();
      options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
      options.setMaxInflight(PUBLISH_THREAD_COUNT * 2);
      options.setConnectionTimeout(INITIALIZE_TIME_MS);

      Basic basicAuth = checkNotNull(endpoint.auth_provider.basic, "basic auth not defined");
      options.setUserName(checkNotNull(basicAuth.username, "MQTT username not defined"));
      options.setPassword(
          checkNotNull(basicAuth.password, "MQTT password not defined").toCharArray());

      client.connect(options);
      return client;
    } catch (Exception e) {
      throw new RuntimeException(message, e);
    }
  }

  private MqttMessage getMqttMessage(Bundle bundle) {
    MqttMessage message = new MqttMessage();
    message.setPayload(stringify(bundle).getBytes());
    return message;
  }

  private String getMqttTopic(Bundle bundle) {
    Envelope envelope = bundle.envelope;
    return envelope == null
        ? String.format(TOPIC_FORMAT, namespace, EXCEPTION_TYPE, EXCEPTION_TYPE)
        : String.format(TOPIC_FORMAT, namespace, envelope.subType, envelope.subFolder);
  }

  private String makeBrokerUrl(EndpointConfiguration endpoint) {
    Transport transport = Optional.ofNullable(endpoint.transport).orElse(Transport.SSL);
    return String.format(BROKER_URL_FORMAT, transport, endpoint.hostname, endpoint.port);
  }

  private String makeClientId() {
    return "client-" + System.currentTimeMillis();
  }

  @Override
  public void activate() {
    super.activate();
    try {
      mqttClient.subscribe(String.format(TOPIC_FORMAT, namespace, TOPIC_WILDCARD, TOPIC_WILDCARD));
    } catch (Exception e) {
      throw new RuntimeException("While subscribing to mqtt topics", e);
    }
  }

  @Override
  public List<Bundle> drainOutput() {
    throw new NotImplementedException("Drain output not implemented");
  }

  private class MqttCallbackHandler implements MqttCallback {

    @Override
    public void connectionLost(Throwable cause) {
      info("Connection lost: " + Common.getExceptionMessage(cause));
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
      info("Delivery complete");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
      sourceQueue.add(message.toString());
    }
  }
}

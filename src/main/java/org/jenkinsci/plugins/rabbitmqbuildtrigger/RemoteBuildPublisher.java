package org.jenkinsci.plugins.rabbitmqbuildtrigger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.rabbitmqconsumer.publishers.PublishChannel;
import org.jenkinsci.plugins.rabbitmqconsumer.publishers.PublishChannelFactory;
import org.jenkinsci.plugins.rabbitmqconsumer.publishers.PublishResult;
import org.kohsuke.stapler.DataBoundConstructor;

import com.rabbitmq.client.AMQP.BasicProperties;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

/**
 * The extension publish build result using rabbitmq.
 *
 * @author rinrinne a.k.a. rin_ne
 */
public class RemoteBuildPublisher extends Notifier {

    private static final Logger LOGGER = Logger.getLogger(RemoteBuildPublisher.class.getName());

    private static final String KEY_PROJECT = "project";
    private static final String KEY_NUMBER = "number";
    private static final String KEY_STATUS = "status";
    private static final String HEADER_JENKINS_URL = "jenkins-url";
    private static final String JSON_CONTENT_TYPE = "application/json";

    private static final String LOG_HEADER = "Publish to RabbitMQ: ";

    private String brokerName;
    private String routingKey;

    /**
     * Creates instance with specified parameters.
     *
     * @param brokerName
     *            the broker name.
     * @param routingKey
     *            the routing key.
     */
    @DataBoundConstructor
    public RemoteBuildPublisher(String brokerName, String routingKey) {
        this.brokerName = brokerName;
        if (StringUtils.isBlank(routingKey)) {
            this.routingKey = RemoteBuildPublisher.class.getPackage().getName();
        } else {
            this.routingKey = routingKey;
        }
    }

    /**
     * Gets broker name.
     *
     * @return the broker name.
     */
    public String getBrokerName() {
        return brokerName;
    }

    /**
     * Sets broker name.
     *
     * @param brokerName
     *            the broker name.
     */
    public void setBrokerName(String brokerName) {
        this.brokerName = brokerName;
    }

    /**
     * Gets routingKey.
     *
     * @return the routingKey.
     */
    public String getRoutingKey() {
        return routingKey;
    }

    /**
     * Sets routingKey.
     *
     * @param routingKey
     *            the routingKey.
     */
    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    /**
     * Gets result as string.
     *
     * @param result
     *            the result.
     * @return the result string.
     */
    private String getResultAsString(Result result) {
        String retStr = "SUCCESS";
        if (result != null) {
            retStr = result.toString();
        }
        return retStr;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {

        if (brokerName == null || brokerName.length() == 0) {
            return true;
        }

        // Generate message (JSON format)
        JSONObject json = new JSONObject();
        json.put(KEY_PROJECT, build.getProject().getName());
        json.put(KEY_NUMBER, build.getNumber());
        json.put(KEY_STATUS, getResultAsString(build.getResult()));

        // Basic property
        BasicProperties.Builder builder = new BasicProperties.Builder();
        builder.appId(RemoteBuildTrigger.PLUGIN_APPID);
        builder.contentType(JSON_CONTENT_TYPE);

        // Header
        Map<String, Object> headers = new HashMap<>();
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null) {
            headers.put(HEADER_JENKINS_URL, jenkins.getRootUrl());
        }
        builder.headers(headers);

        // Publish message
        PublishChannel ch = PublishChannelFactory.getPublishChannel();
        if (ch != null && ch.isOpen()) {
            // return value is not needed if you don't need to wait.
            Future<PublishResult> future = ch.publish(brokerName, routingKey, builder.build(),
                    json.toString().getBytes(StandardCharsets.UTF_8));

            // Wait until publish is completed.
            try {
                PublishResult result = future.get();

                if (result.isSuccess()) {
                    listener.getLogger().println(LOG_HEADER + "Success.");
                } else {
                    listener.getLogger().println(LOG_HEADER + "Fail - " + result.getMessage());
                }
            } catch (Exception e) {
                LOGGER.warning(e.getMessage());
                listener.getLogger().println(LOG_HEADER + "Fail due to exception.");
            }
        } else {
          listener.getLogger().println(LOG_HEADER + "Unpublished due to no avaiable publish channel");
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * The descriptor for this publisher.
     *
     * @author rinrinne a.k.a. rin_ne
     */
    @Extension
    @Symbol("rmqRemotePublish")
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> project) {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.RabbitMQBuildPublisher();
        }
    }
}

package org.jenkinsci.plugins.rabbitmqbuildtrigger;

import org.kohsuke.stapler.export.Exported;

import hudson.model.Cause;

/**
 * Cause class for remote build.
 *
 * @author rinrinne a.k.a. rin_ne
 */
public class RemoteBuildCause extends Cause {

    private final String queueName;

    /**
     * Creates instance with specified parameter.
     *
     * @param queueName
     *            the queue name.
     */
    public RemoteBuildCause(String queueName) {
        this.queueName = queueName;
    }

    @Override
    @Exported(visibility = 3)
    public String getShortDescription() {
        return "Triggered by remote build message from RabbitMQ queue: " + queueName;
    }

}

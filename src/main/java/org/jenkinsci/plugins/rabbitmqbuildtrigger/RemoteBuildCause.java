package org.jenkinsci.plugins.rabbitmqbuildtrigger;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.export.Exported;

import hudson.model.Cause;

/**
 * Cause class for remote build.
 * 
 * @author rinrinne a.k.a. rin_ne
 */
public class RemoteBuildCause extends Cause {

    private String queueName;

    private String reason;

    /**
     * Creates a new cause with the given queue.
     * 
     * @param queueName
     *            the queue name.
     */
    public RemoteBuildCause(String queueName) {
        this(queueName, null);
    }

    public RemoteBuildCause(String queueName, String reason) {
        this.queueName = queueName;
        this.reason = StringUtils.isNotBlank(reason) ? " (" + reason + ")" : StringUtils.EMPTY;
    }

    @Override
    @Exported(visibility = 3)
    public String getShortDescription() {
        return "Triggered RabbitMQ message on queue: " + queueName + reason;
    }
}

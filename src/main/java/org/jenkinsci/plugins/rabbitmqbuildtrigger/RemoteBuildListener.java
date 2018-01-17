package org.jenkinsci.plugins.rabbitmqbuildtrigger;

import hudson.Extension;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.jenkinsci.plugins.rabbitmqconsumer.extensions.MessageQueueListener;

import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The extension listen application message then call triggers.
 *
 * @author rinrinne a.k.a. rin_ne
 */
@Extension
public class RemoteBuildListener extends MessageQueueListener {
    private static final String PLUGIN_NAME = "Remote Builder";

    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String KEY_PROJECT = "project";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_PARAMETER = "parameter";
    private static final String KEY_REASON = "reason";

    private static final Logger LOGGER = Logger.getLogger(RemoteBuildListener.class.getName());

    private final Set<RemoteBuildTrigger> triggers = new CopyOnWriteArraySet<RemoteBuildTrigger>();

    @Override
    public String getName() {
        return PLUGIN_NAME;
    }

    @Override
    public String getAppId() {
        return RemoteBuildTrigger.PLUGIN_APPID;
    }

    /**
     * Get triggers.
     *
     *       the triggers.
     */
    public  Set<RemoteBuildTrigger> getTriggers(){
        return this.triggers;
    }
    
    /**
     * Adds trigger.
     *
     * @param trigger
     *            the trigger.
     */
    public void addTrigger(RemoteBuildTrigger trigger) {
        triggers.add(trigger);
    }

    /**
     * Removes trigger.
     *
     * @param trigger
     *            the trigger.
     */
    public void removeTrigger(RemoteBuildTrigger trigger) {
        triggers.remove(trigger);
    }

    @Override
    public void onBind(String queueName) {
        LOGGER.info("Bind to: " + queueName);
    }

    @Override
    public void onUnbind(String queueName) {
        LOGGER.info("Unbind from: " + queueName);
    }

    /**
     * Finds matched projects using given project name and token then schedule
     * build.
     */
    @Override
    public void onReceive(String queueName, String contentType, Map<String, Object> headers, byte[] body) {
        if (CONTENT_TYPE_JSON.equals(contentType)) {
            try {
                String msg = new String(body, "UTF-8");
                try {
                    JSONObject json = (JSONObject) JSONSerializer.toJSON(msg);
                    Pattern targetProject = Pattern.compile(json.getString(KEY_PROJECT));
                    String messageToken = json.getString(KEY_TOKEN);
                    JSONArray parameters = json.has(KEY_PARAMETER) ? json.getJSONArray(KEY_PARAMETER) : null;
                    String reason = json.has(KEY_REASON) ? json.getString(KEY_REASON) : null;

                    LOGGER.log(Level.FINE, "Received message for project: {0}", targetProject.toString());

                    for (RemoteBuildTrigger t : triggers) {
                        if (t.getRemoteBuildToken() == null) {
                            LOGGER.log(Level.WARNING, "ignoring AMQP trigger for project {0}: no token set", t.getProjectName());
                            continue;
                        }

                        if (t.getRemoteBuildToken().equals(messageToken) && targetProject.matcher(t.getProjectName()).matches()) {
                            LOGGER.log(Level.FINE, "Triggering project: {0}", t.getProjectName());
                            t.scheduleBuild(queueName, reason, parameters);
                        }
                    }
                } catch (JSONException e) {
                    LOGGER.warning("JSON format string: " + msg);
                    LOGGER.warning(e.getMessage());
                }
            } catch (UnsupportedEncodingException e) {
                LOGGER.warning("Unsupported encoding. Is message body is not string?");
            }
        }
    }
}

package org.jenkinsci.plugins.rabbitmqbuildtrigger;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.ParameterValue;
import hudson.model.AbstractProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Project;
import hudson.model.StringParameterValue;
import hudson.model.listeners.ItemListener;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.rabbitmqconsumer.extensions.MessageQueueListener;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * The extension trigger builds by application message.
 *
 * @author rinrinne a.k.a. rin_ne
 */
public class RemoteBuildTrigger extends Trigger<AbstractProject<?, ?>> {

    public static final String PLUGIN_APPID = "remote-build";

    private static final String PLUGIN_NAME = Messages.RabbitMQBuildTrigger();

    private static final String KEY_PARAM_NAME = "name";
    private static final String KEY_PARAM_VALUE = "value";

    private static final Logger LOGGER = Logger.getLogger(RemoteBuildTrigger.class.getName());

    private String remoteBuildToken;
    private String queue;

    /**
     * Creates instance with specified parameters.
     *
     * @param remoteBuildToken
     *            the token for remote build.
     */
    @DataBoundConstructor
    public RemoteBuildTrigger(String remoteBuildToken, String queue) {
        super();
        this.remoteBuildToken = StringUtils.stripToNull(remoteBuildToken);
        this.queue = queue;
    }

    @Override
    public void start(AbstractProject<?, ?> project, boolean newInstance) {
        RemoteBuildListener listener = MessageQueueListener.all().get(RemoteBuildListener.class);

        if (listener != null) {
            listener.addTrigger(this);
        }
        super.start(project, newInstance);
        removeDuplicatedTrigger(listener.getTriggers());
    }

    @Override
    public void stop() {
        MessageQueueListener.all().get(RemoteBuildListener.class).removeTrigger(this);
        super.stop();
    }

    /**
     * Remove the duplicated trigger from the triggers.
     *
     * @param triggers 
     *          the set of current trigger instances which have already been loaded in the memory 
     */
    public void removeDuplicatedTrigger(Set<RemoteBuildTrigger> triggers){
        Map<String,RemoteBuildTrigger>  tempHashMap= new HashMap<String,RemoteBuildTrigger>(); 
        for(RemoteBuildTrigger trigger:triggers){
            tempHashMap.put(trigger.getProjectName(), trigger);
        }    
        triggers.clear();
        triggers.addAll(tempHashMap.values());
    }
    
    /**
     * Gets token.
     *
     * @return the token.
     */
    public String getRemoteBuildToken() {
        return remoteBuildToken;
    }

    /**
     * Sets token.
     *
     * @param remoteBuildToken the token.
     */
    public void setRemoteBuildToken(String remoteBuildToken) {
        this.remoteBuildToken = remoteBuildToken;
    }

    /**
     * Get queue
     * @return the queue.
     */
    public String getQueue() { return queue; }

    /**
     *
     * @param queue the queue.
     */
    public void setQueue(String queue) { this.queue = queue; }

    /**
     * Gets project name.
     *
     * @return the project name.
     */
    public String getProjectName() {
        if(job!=null){
            return job.getName();
        }
        return "";
    }

    /**
     * Schedules build for triggered job using application message.
     *
     * @param queueName
     *            the queue name.
     * @param jsonArray
     *            the content of application message.
     */
    public void scheduleBuild(String queueName, JSONArray jsonArray) {
        if (jsonArray != null) {
            List<ParameterValue> parameters = getUpdatedParameters(jsonArray, getDefinitionParameters(job));
            job.scheduleBuild2(0, new RemoteBuildCause(queueName), new ParametersAction(parameters));
        } else {
            job.scheduleBuild2(0, new RemoteBuildCause(queueName));
        }
    }

    /**
     * Gets updated parameters in job.
     *
     * @param jsonParameters
     *            the array of JSONObjects.
     * @param definedParameters
     *            the list of defined paramters.
     * @return the list of parameter values.
     */
    private List<ParameterValue> getUpdatedParameters(JSONArray jsonParameters, List<ParameterValue> definedParameters) {
        List<ParameterValue> newParams = new ArrayList<ParameterValue>();
        for (ParameterValue defParam : definedParameters) {

            for (int i = 0; i < jsonParameters.size(); i++) {
                JSONObject jsonParam = jsonParameters.getJSONObject(i);

                if (defParam.getName().toUpperCase().equals(jsonParam.getString(KEY_PARAM_NAME).toUpperCase())) {
                    newParams.add(new StringParameterValue(defParam.getName(), jsonParam.getString(KEY_PARAM_VALUE), defParam.getDescription()));
                }
            }
        }
        return newParams;
    }

    /**
     * Gets definition parameters.
     *
     * @param project
     *            the project.
     * @return the list of parameter values.
     */
    private List<ParameterValue> getDefinitionParameters(AbstractProject<?, ?> project) {
        List<ParameterValue> parameters = new ArrayList<ParameterValue>();
        ParametersDefinitionProperty properties = (ParametersDefinitionProperty) project
                .getProperty(ParametersDefinitionProperty.class);

        if (properties != null) {
            for (ParameterDefinition paramDef : properties.getParameterDefinitions()) {
                ParameterValue param = paramDef.getDefaultParameterValue();
                if (param != null) {
                    parameters.add(param);
                }
            }
        }

        return parameters;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * The descriptor for this trigger.
     *
     * @author rinrinne a.k.a. rin_ne
     */
    @Extension
    public static class DescriptorImpl extends TriggerDescriptor {

        @Override
        public boolean isApplicable(Item item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return PLUGIN_NAME;
        }

        /**
         * ItemListener implementation class.
         *
         * @author rinrinne a.k.a. rin_ne
         */
        @Extension
        public static class ItemListenerImpl extends ItemListener {

            @Override
            public void onLoaded() {
                RemoteBuildListener listener = MessageQueueListener.all().get(RemoteBuildListener.class);
                for (Project<?, ?> p : Jenkins.getInstance().getAllItems(Project.class)) {
                    RemoteBuildTrigger t = p.getTrigger(RemoteBuildTrigger.class);
                    if (t != null) {
                        listener.addTrigger(t);
                    }
                }
            }
        }
    }
}

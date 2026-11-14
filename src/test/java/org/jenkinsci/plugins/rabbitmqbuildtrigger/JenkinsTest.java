package org.jenkinsci.plugins.rabbitmqbuildtrigger;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.Future;

import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.rabbitmqconsumer.extensions.MessageQueueListener;
import org.jenkinsci.plugins.rabbitmqconsumer.publishers.PublishChannel;
import org.jenkinsci.plugins.rabbitmqconsumer.publishers.PublishChannelFactory;
import org.jenkinsci.plugins.rabbitmqconsumer.publishers.PublishResult;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.rabbitmq.client.AMQP;

import hudson.Functions;
import hudson.model.Build;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Project;
import hudson.model.StringParameterDefinition;
import hudson.tasks.BatchFile;
import hudson.tasks.Builder;
import hudson.tasks.Shell;
import jenkins.model.Jenkins;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class JenkinsTest {
    // CS IGNORE VisibilityModifier FOR NEXT 3 LINES. REASON: Mocks tests.
    @Rule
    public JenkinsRule j = new JenkinsRule();

    Builder createBuilder(Boolean isWindows) {
        if (isWindows) {
            return new BatchFile("echo TRIGGERED");
        } else {
            return new Shell("echo TRIGGERED");
        }
    }

    @Test
    public void testTriggerBuild() throws Exception {
        RemoteBuildTrigger trigger = new RemoteBuildTrigger("trigger-token");
        FreeStyleProject project = j.createFreeStyleProject("triggered-project");
        project.addTrigger(trigger);
        project.getBuildersList().add(createBuilder(Functions.isWindows()));
        trigger.start(project, false);

        String msg = "{\"project\":\"triggered-project\",\"token\":\"trigger-token\"}";
        RemoteBuildListener listener = MessageQueueListener.all().get(RemoteBuildListener.class);
        listener.onReceive("trigger-queue", "application/json", null, msg.getBytes("UTF-8"));

        waitForBuildCompleted(project);

        FreeStyleBuild build = project.getLastBuild();
        String s = FileUtils.readFileToString(build.getLogFile());
        assertThat(s, containsString("TRIGGERED"));
    }

    @Test
    public void testNonTriggerBuild() throws Exception {
        RemoteBuildTrigger trigger = new RemoteBuildTrigger("trigger-token");
        FreeStyleProject project = j.createFreeStyleProject("triggered-project2");
        project.addTrigger(trigger);
        project.getBuildersList().add(createBuilder(Functions.isWindows()));
        trigger.start(project, false);

        String msg = "{\"project\":\"triggered-project\",\"token\":\"trigger-token\"}";
        RemoteBuildListener listener = MessageQueueListener.all().get(RemoteBuildListener.class);
        listener.onReceive("trigger-queue", "application/json", null, msg.getBytes("UTF-8"));

        try {
            Thread.sleep(3000);
        } catch (Exception e) {
            // no operation
        }

        assertThat(project.getBuilds().isEmpty(), is(true));
    }

    @Test
    public void testTriggerWithPublisher() throws Exception {
        PublishChannel channel = Mockito.mock(PublishChannel.class);
        @SuppressWarnings("unchecked")
        Future<PublishResult> future = Mockito.mock(Future.class);
        Mockito.when(channel.isOpen()).thenReturn(true);
        Mockito.when(channel.publish(Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(future);
        Mockito.when(future.get()).thenReturn(new PublishResult(true, "", ""));
        RemoteBuildPublisher.channelSupplier = () -> channel;
        try {
            runPublisherScenario(channel);
        } finally {
            RemoteBuildPublisher.channelSupplier = PublishChannelFactory::getPublishChannel;
        }
    }

    private void runPublisherScenario(PublishChannel channel) throws Exception {
        RemoteBuildTrigger trigger = new RemoteBuildTrigger("trigger-token");
        RemoteBuildPublisher publisher = new RemoteBuildPublisher("exchange", "routing-key");
        FreeStyleProject project = j.createFreeStyleProject("triggered-project-publisher");
        project.addTrigger(trigger);
        project.getPublishersList().add(publisher);
        project.getBuildersList().add(createBuilder(Functions.isWindows()));
        trigger.start(project, false);

        String msg = "{\"project\":\"triggered-project-publisher\",\"token\":\"trigger-token\"}";
        RemoteBuildListener listener = MessageQueueListener.all().get(RemoteBuildListener.class);
        listener.onReceive("trigger-queue", "application/json", null, msg.getBytes("UTF-8"));

        waitForBuildCompleted(project);

        FreeStyleBuild build = project.getLastBuild();
        String s = FileUtils.readFileToString(build.getLogFile());
        assertThat(s, containsString("TRIGGERED"));

        ArgumentCaptor<String> exchangeName = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> routingKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AMQP.BasicProperties> props = ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        Mockito.verify(channel, Mockito.times(1)).publish(exchangeName.capture(), routingKey.capture(),
                props.capture(), Mockito.any());

        assertThat(exchangeName.getValue(), is("exchange"));
        assertThat(routingKey.getValue(), is("routing-key"));
        assertThat(props.getValue().getAppId(), is(RemoteBuildTrigger.PLUGIN_APPID));
        assertThat(props.getValue().getContentType(), is("application/json"));
        assertThat(props.getValue().getHeaders().get("jenkins-url").toString(), is(Jenkins.get().getRootUrl()));
    }

    @Test
    public void testTriggerBuildWithParameter() throws Exception {
        RemoteBuildTrigger trigger = new RemoteBuildTrigger("trigger-token");
        StringParameterDefinition definition = new StringParameterDefinition("HOGE", "");
        ParametersDefinitionProperty property = new ParametersDefinitionProperty(definition);
        FreeStyleProject project = j.createFreeStyleProject("triggered-project-with-parameter");
        project.addProperty(property);
        project.addTrigger(trigger);
        project.getBuildersList().add(createBuilder(Functions.isWindows()));
        trigger.start(project, false);

        String msg = "{\"project\":\"triggered-project-with-parameter\",\"token\":\"trigger-token\","
                + "\"parameter\":[{\"name\":\"HOGE\",\"value\":\"fuga\"}]}";
        RemoteBuildListener listener = MessageQueueListener.all().get(RemoteBuildListener.class);
        listener.onReceive("trigger-queue", "application/json", null, msg.getBytes("UTF-8"));

        waitForBuildCompleted(project);

        FreeStyleBuild build = project.getLastBuild();
        String s = FileUtils.readFileToString(build.getLogFile());
        assertThat("This is untriggered build.", s, containsString("TRIGGERED"));
        ParametersAction act = build.getAction(ParametersAction.class);
        assertThat("Parameter is not 1.", act.getParameters().size(), is(1));
        ParameterValue p = act.getParameters().get(0);
        assertThat("Unknown parameter key.", p.getName(), containsString("HOGE"));
        assertThat("Unknown parameter value.", p.getShortDescription(), containsString("fuga"));
    }

    private void waitForBuildCompleted(Project<?, ?> project) throws Exception {
        int cnt = 0;

        while (project.getBuilds().isEmpty()) {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                // no operation
            }
            cnt++;
            if (cnt > 10) {
                throw new Exception("Time out.");
            }
        }

        cnt = 0;
        Build<?, ?> build = project.getFirstBuild();

        while (build.isBuilding()) {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                // no operation
            }
            cnt++;
            if (cnt > 10) {
                throw new Exception("Time out.");
            }
        }
    }
}
